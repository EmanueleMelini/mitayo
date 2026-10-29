package it.emanuelemelini.mitayo.data.anilist

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.emanuelemelini.mitayo.BuildConfig
import it.emanuelemelini.mitayo.MitayoApp
import it.emanuelemelini.mitayo.data.FlushResult
import it.emanuelemelini.mitayo.data.ListMirror
import it.emanuelemelini.mitayo.data.Outbox
import it.emanuelemelini.mitayo.data.auth.LoginProblem
import it.emanuelemelini.mitayo.data.auth.LoginState
import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.db.AppDatabase
import it.emanuelemelini.mitayo.data.db.ListEntry
import it.emanuelemelini.mitayo.data.db.ListStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface AniListSyncState {
    data object Idle : AniListSyncState

    data object Syncing : AniListSyncState

    data class Failed(val error: Throwable) : AniListSyncState
}

/** Which list wins a conflict. */
enum class ListSide { MAL, ANILIST }

/**
 * The AniList link, optional next to MAL: the login, the sync of the two lists after every MAL
 * sync ([Reconciler]), the changes made in Mitayo copied to AniList, the conflicts the user solves.
 * Off and hidden when the build has no AniList client ID ([enabled]): an update without it
 * removes the link (see [onAppStart]).
 */
class AniListSync(
    context: Context,
    private val api: AniListApi,
    private val store: AniListStore,
    private val database: AppDatabase,
    private val malOutbox: Outbox,
    private val scope: CoroutineScope,
) : ListMirror {
    private val workManager = WorkManager.getInstance(context)
    private val dao = database.aniListDao()
    private val logDao = database.aniListLogDao()

    /** Sync, flush and conflict choices touch the same entries: one at a time. */
    private val mutex = Mutex()

    /**
     * Held by a sync from the download of the AniList list to its decisions, and by each send: an
     * anime sent in between would be missing from the downloaded list, as if removed from AniList.
     * Taken before [mutex].
     */
    private val aniListLock = Mutex()
    private var syncJob: Job? = null

    val enabled: Boolean get() = BuildConfig.ANILIST_CLIENT_ID.isNotEmpty()

    val info: Flow<AniListInfo> = store.info
    val pendingCount: Flow<Int> = dao.observeOpCount()
    val conflictCount: Flow<Int> = dao.observeConflictCount()

    /** The anime of the MAL list that stay on MAL only: AniList hasn't them. */
    val notOnAniList: Flow<List<MissingAnime>> = dao.observeNotOnAniList()
    val conflicts: Flow<List<AniListConflict>> = dao.observeConflicts()

    /** The sync log, the last session first. */
    val sessions: Flow<List<AniListSessionSummary>> = logDao.observeSessions()

    /** The syncs with an in-app notice still to show. */
    val unnoticedSessions: Flow<List<AniListSessionSummary>> = logDao.observeUnnoticed()

    fun session(id: Long): Flow<AniListSessionSummary?> = logDao.observeSession(id)

    /** What [sessionId] did, the last first. */
    fun events(sessionId: Long): Flow<List<AniListSessionEvent>> = logDao.observeEvents(sessionId)

    /** [notice] of [AniListSession]: shown. */
    suspend fun markNoticed(sessionId: Long, notice: Int) = logDao.setNotice(sessionId, notice)

    /** "Got it": the anime stays on the "Not on AniList" page, out of the way. */
    suspend fun acknowledge(malId: Int) = logDao.acknowledge(AniListAcknowledged(malId, System.currentTimeMillis()))

    suspend fun unacknowledge(malId: Int) = logDao.unacknowledge(malId)

    private val _state = MutableStateFlow<AniListSyncState>(AniListSyncState.Idle)
    val state: StateFlow<AniListSyncState> = _state.asStateFlow()

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    /** The AniList page to open in a Custom Tab; null when the build has no client ID. */
    suspend fun authorizeUri(): Uri? {
        if (!enabled) {
            _loginState.value = LoginState(problem = LoginProblem.MISSING_CLIENT_ID)
            return null
        }
        _loginState.value = LoginState()
        store.setPendingLogin(System.currentTimeMillis())
        return AniListLogin.authorizeUri(BuildConfig.ANILIST_CLIENT_ID)
    }

    /** The browser came back to mitayo://anilist. */
    fun handleRedirect(uri: Uri) {
        scope.launch {
            val pendingSince = store.pendingLogin()
            store.setPendingLogin(null)
            // A redirect that isn't the answer to our own request is ignored
            if (!enabled || pendingSince == null || System.currentTimeMillis() - pendingSince > LOGIN_MAX_AGE_MS) {
                _loginState.value = LoginState(problem = LoginProblem.FAILED)
                return@launch
            }
            val token = when (val redirect = AniListLogin.parseRedirect(uri.fragment, uri.getQueryParameter("error"))) {
                AniListLogin.Redirect.Cancelled -> {
                    _loginState.value = LoginState(problem = LoginProblem.CANCELLED)
                    return@launch
                }
                AniListLogin.Redirect.Failed -> {
                    _loginState.value = LoginState(problem = LoginProblem.FAILED)
                    return@launch
                }
                is AniListLogin.Redirect.Token -> redirect
            }
            _loginState.value = LoginState(working = true)
            val user = try {
                api.viewer(token.token)
            } catch (_: IOException) {
                _loginState.value = LoginState(problem = LoginProblem.NETWORK)
                return@launch
            } catch (_: Exception) {
                _loginState.value = LoginState(problem = LoginProblem.FAILED)
                return@launch
            }
            // A new link starts from scratch: the first sync merges the two lists
            clearTables()
            store.link(token.token, System.currentTimeMillis() + token.expiresInSeconds * 1000L, user)
            _loginState.value = LoginState()
            onListSynced()
        }
    }

    /** The link goes, with what Mitayo knows of the AniList list; the list on AniList stays as it is. */
    fun unlink() {
        scope.launch { drop(expired = false) }
    }

    /**
     * At every start: a build without the client ID doesn't keep a link made by an earlier one;
     * otherwise the changes saved offline leave at the first chance.
     */
    suspend fun onAppStart() {
        if (!enabled) {
            if (store.hasToken()) drop(expired = false)
        } else if (store.currentInfo().account != null) {
            scheduleFlush()
        }
    }

    /** Also from the Profile, with the MAL list: the MAL sync calls [onListSynced] when done. */
    override fun onListSynced() {
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            if (store.currentInfo().account == null) return@launch
            if (store.token() == null) {
                drop(expired = true)
                return@launch
            }
            _state.value = AniListSyncState.Syncing
            val startedAt = System.currentTimeMillis()
            _state.value = try {
                aniListLock.withLock { sync(startedAt) }
                AniListSyncState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (_: AniListUnauthorizedException) {
                drop(expired = true)
                AniListSyncState.Idle
            } catch (e: Exception) {
                logFailure(startedAt, e)
                AniListSyncState.Failed(e)
            }
        }
    }

    override suspend fun mirror(animeId: Int, status: ListStatus?, episodesWatched: Int, score: Int?) {
        if (store.currentInfo().account == null) return
        enqueue(animeId, status?.let(AniListStatus::fromMal), episodesWatched, score?.let(Scores::toAniList))
    }

    override suspend fun clear() = drop(expired = false)

    /** The anime already downloaded from MAL (of the conflicts, of the log), for their titles and posters. */
    suspend fun anime(ids: List<Int>): Map<Int, Anime> = ids.chunked(TITLES_PAGE).flatMap { dao.anime(it) }.associateBy { it.id }

    /** Solves the conflict of [malId]: the other list takes what [keep] has. */
    suspend fun resolve(malId: Int, keep: ListSide) {
        mutex.withLock {
            database.withTransaction {
                val sessionId = appSessionId()
                dao.conflict(malId)?.let { resolveLocked(it, keep, sessionId) }
            }
        }
        logDao.closeFinished(System.currentTimeMillis())
        scheduleFlush()
    }

    suspend fun resolveAll(keep: ListSide) {
        mutex.withLock {
            database.withTransaction {
                val sessionId = appSessionId()
                dao.conflicts().forEach { resolveLocked(it, keep, sessionId) }
            }
        }
        logDao.closeFinished(System.currentTimeMillis())
        scheduleFlush()
    }

    /** Sends the changes to AniList, oldest first; each one's answer becomes the base of the next sync. */
    suspend fun flush(): FlushResult {
        if (store.token() == null) {
            // Linked once, and the year is over
            if (store.hasToken()) drop(expired = true)
            return FlushResult.NOT_LOGGED_IN
        }
        while (true) {
            val op = dao.firstOp() ?: run {
                logDao.closeFinished(System.currentTimeMillis())
                return FlushResult.DONE
            }
            // The changes saved before the sessions go in the app's
            val sessionId = op.sessionId ?: database.withTransaction { appSessionId() }
            try {
                aniListLock.withLock { mutex.withLock { send(op, sessionId) } }
            } catch (_: AniListUnauthorizedException) {
                drop(expired = true)
                return FlushResult.NOT_LOGGED_IN
            } catch (e: AniListHttpException) {
                // 403 is AniList switched off for a while; other client errors won't improve by
                // retrying, and the next sync shows the real state again
                if (e.code == 403 || e.code == 429 || e.code >= 500) {
                    logWaiting(sessionId, e.code)
                    return FlushResult.RETRY
                }
                dao.deleteOp(op.id)
                log(sessionId, listOf(event(SyncEventKind.SEND_FAILED, op.malId, code = e.code)))
            } catch (_: IOException) {
                logWaiting(sessionId, -1)
                return FlushResult.RETRY
            }
            logDao.closeFinished(System.currentTimeMillis())
        }
    }

    /**
     * Downloads the AniList list and decides anime by anime. What it does goes in a session of the
     * log, kept only when something changed; the changes for AniList leave with the flush.
     */
    private suspend fun sync(startedAt: Long) {
        val user = api.viewer()
        val adultBefore = store.currentInfo().account?.adultContent
        // On the "Not on AniList" page before this sync: the ones leaving it were found
        val notFoundBefore = dao.notFoundIds().toSet()
        store.updateUser(user)
        // 18+ just turned on: the adult anime not found so far are looked up now, not in a week
        if (adultBefore == false && user.options?.displayAdultContent == true) dao.clearNotFound()
        val format = ScoreFormat.fromApi(user.mediaListOptions?.scoreFormat)
        val now = System.currentTimeMillis()

        var notOnMal = 0
        val aniList = HashMap<Int, AniListListEntry>()
        val listEntries = api.animeList(user.id)
        listEntries.forEach { entry ->
            val malId = entry.media.idMal
            if (malId == null) notOnMal++ else aniList.putIfAbsent(malId, entry)
        }

        // The anime of the MAL list not in the AniList one: where they are on AniList, if anywhere
        val known = dao.entries().associateBy { it.malId }
        val toLookUp = dao.malList().map { it.animeId }.filter { it !in aniList && known[it].needsLookup(now) }
        lookUp(toLookUp, now)
        val steps = listOfNotNull(
            event(SyncEventKind.LIST_READ, at = now, count = listEntries.size),
            event(SyncEventKind.LOOKED_UP, at = now, count = toLookUp.size).takeIf { toLookUp.isNotEmpty() },
        )

        mutex.withLock {
            val entries = dao.entries().associateBy { it.malId }
            val mal = dao.malList().associateBy { it.animeId }
            val pending = (dao.pendingIds() + dao.malPendingIds()).toSet()
            val conflicts = dao.conflicts().associateBy { it.malId }
            val updated = mutableListOf<AniListEntry>()
            val findings = mutableListOf<AniListSessionEvent>()
            val found = mutableListOf<Int>()
            database.withTransaction {
                val sessionId = logDao.insertSession(
                    AniListSession(kind = SyncSessionKind.SYNC, state = SyncSessionState.RUNNING, startedAt = startedAt, updatedAt = now)
                )
                for (malId in mal.keys + aniList.keys + entries.keys) {
                    val malSide = mal[malId]?.side()
                    val aniListEntry = aniList[malId]
                    val aniListSide = aniListEntry?.side()
                    val mediaId = aniListEntry?.media?.id ?: entries[malId]?.mediaId
                    // Not on AniList: listed by notOnAniList, looked up again in a week
                    if (mediaId == null) {
                        if (malSide != null && malId !in notFoundBefore) findings += event(SyncEventKind.NOT_FOUND, malId, at = now)
                        continue
                    }
                    if (malSide != null && malId in notFoundBefore) {
                        findings += event(SyncEventKind.FOUND, malId, at = now)
                        found += malId
                    }
                    // A change still on its way: the next sync sees where it arrived
                    if (malId in pending) continue
                    var entry = (entries[malId] ?: AniListEntry(malId, mediaId, now))
                        .copy(mediaId = mediaId, checkedAt = now, entryId = aniListEntry?.id)
                    val conflict = conflicts[malId]
                    if (conflict != null) {
                        // Solved outside Mitayo, or still open with the latest values
                        if (Reconciler.same(malSide, aniListSide, format)) {
                            dao.deleteConflict(malId)
                            entry = entry.withBase(malSide, aniListSide)
                        } else {
                            dao.upsertConflict(AniListConflict.of(malId, aniListEntry.title() ?: conflict.title, malSide, aniListSide, conflict.foundAt))
                        }
                        updated += entry
                        continue
                    }
                    when (val decision = Reconciler.decide(malSide, aniListSide, entry.base, format)) {
                        Decision.Agree -> entry = entry.withBase(malSide, aniListSide)
                        is Decision.Write -> {
                            val newMal = decision.mal?.let { write -> writeMal(malId, write).also { findings += malEvent(malId, it, now) } }
                                ?: malSide
                            decision.aniList?.let { enqueueLocked(malId, it.status, it.progress, it.scoreRaw, sessionId) }
                            // The AniList side becomes AniList's answer when the change arrives
                            entry = entry.withBase(newMal, aniListSide)
                        }
                        Decision.Conflict -> {
                            dao.upsertConflict(AniListConflict.of(malId, aniListEntry.title(), malSide, aniListSide, now))
                            findings += event(SyncEventKind.CONFLICT, malId, at = now, title = aniListEntry.title())
                        }
                    }
                    updated += entry
                }
                dao.upsertEntries(updated)
                if (found.isNotEmpty()) logDao.unacknowledge(found)
                val queued = dao.opCount(sessionId)
                if (findings.isEmpty() && queued == 0) {
                    // Nothing changed: not kept in the log
                    logDao.deleteSession(sessionId)
                } else {
                    log(sessionId, steps + findings)
                    if (queued == 0) logDao.setState(sessionId, SyncSessionState.DONE, now, endedAt = now)
                    trimLog()
                }
            }
            store.setSynced(now, notOnMal)
        }
        scheduleFlush()
    }

    /** A sync that stopped stays in the log, even without changes. */
    private suspend fun logFailure(startedAt: Long, error: Exception) {
        val now = System.currentTimeMillis()
        database.withTransaction {
            val sessionId = logDao.insertSession(
                AniListSession(kind = SyncSessionKind.SYNC, state = SyncSessionState.FAILED, startedAt = startedAt, updatedAt = now, endedAt = now)
            )
            log(sessionId, listOf(event(SyncEventKind.ERROR, at = now, code = errorCode(error))))
            trimLog()
        }
    }

    /**
     * The session of the changes made in the app: the last one while they come less than
     * [APP_SESSION_GAP_MS] apart, otherwise a new one. In a transaction with what goes in it.
     */
    private suspend fun appSessionId(): Long {
        val now = System.currentTimeMillis()
        logDao.recentAppSession(now - APP_SESSION_GAP_MS)?.let { recent ->
            logDao.setState(recent.id, SyncSessionState.RUNNING, now, endedAt = null)
            return recent.id
        }
        val id = logDao.insertSession(AniListSession(kind = SyncSessionKind.APP, state = SyncSessionState.RUNNING, startedAt = now, updatedAt = now))
        trimLog()
        return id
    }

    /** Saves [events] in [sessionId], with the titles of their anime. */
    private suspend fun log(sessionId: Long, events: List<AniListSessionEvent>) {
        if (events.isEmpty()) return
        val titles = events.mapNotNull { it.malId }.distinct().chunked(TITLES_PAGE)
            .flatMap { dao.anime(it) }
            .associate { it.id to it.title }
        logDao.insertEvents(events.map { it.copy(sessionId = sessionId, title = it.malId?.let(titles::get) ?: it.title) })
        logDao.touch(sessionId, events.maxOf { it.at })
    }

    /** AniList busy or offline: said once while it lasts. */
    private suspend fun logWaiting(sessionId: Long, code: Int) {
        val last = logDao.lastEvent(sessionId)
        if (last?.kind == SyncEventKind.WAITING && last.code == code) return
        log(sessionId, listOf(event(SyncEventKind.WAITING, code = code)))
    }

    private suspend fun trimLog() {
        logDao.trimSessions(KEPT_SESSIONS)
        logDao.deleteOrphanEvents()
    }

    private fun event(
        kind: SyncEventKind,
        malId: Int? = null,
        at: Long = System.currentTimeMillis(),
        title: String? = null,
        status: String? = null,
        episodes: Int = 0,
        score: Int = 0,
        code: Int? = null,
        count: Int = 0,
    ) = AniListSessionEvent(
        sessionId = 0,
        at = at,
        kind = kind,
        malId = malId,
        title = title,
        status = status,
        episodes = episodes,
        score = score,
        code = code,
        count = count,
    )

    /** What [writeMal] did to [malId]. */
    private fun malEvent(malId: Int, side: MalSide?, at: Long) = if (side == null) {
        event(SyncEventKind.REMOVED_FROM_MAL, malId, at = at)
    } else {
        event(SyncEventKind.TO_MAL, malId, at = at, status = side.status.apiValue, episodes = side.episodes, score = side.score)
    }

    /** Looks [malIds] up on AniList, [AniListApi.MEDIA_PAGE] a request; the ones not found are marked as such. */
    private suspend fun lookUp(malIds: List<Int>, now: Long) {
        malIds.chunked(AniListApi.MEDIA_PAGE).forEach { chunk ->
            val found = api.mediaByMalIds(chunk).mapNotNull { media -> media.idMal?.let { it to media.id } }.toMap()
            val entries = chunk.map { malId ->
                (dao.entry(malId) ?: AniListEntry(malId, null, now)).copy(mediaId = found[malId], checkedAt = now)
            }
            dao.upsertEntries(entries)
        }
    }

    private suspend fun send(op: AniListOp, sessionId: Long) {
        val malSide = database.listDao().get(op.malId)?.side()
        val entry = dao.entry(op.malId)
        val status = op.status
        if (status == null) {
            entry?.entryId?.let {
                api.delete(it)
                log(sessionId, listOf(event(SyncEventKind.REMOVED_FROM_ANILIST, op.malId)))
            }
            entry?.let { dao.upsertEntry(it.copy(entryId = null).withBase(malSide, null)) }
        } else {
            val now = System.currentTimeMillis()
            val lookUpNow = entry?.mediaId == null && entry.needsLookup(now)
            val mediaId = entry?.mediaId ?: if (lookUpNow) {
                lookUp(listOf(op.malId), now)
                dao.entry(op.malId)?.mediaId
            } else {
                null
            }
            // AniList hasn't this anime: nothing to send
            if (mediaId != null) {
                val saved = api.save(mediaId, status, op.progress, op.scoreRaw)
                val current = dao.entry(op.malId) ?: AniListEntry(op.malId, mediaId, now)
                dao.upsertEntry(current.copy(mediaId = mediaId, entryId = saved.id).withBase(malSide, saved.side()))
                val sent = event(
                    SyncEventKind.TO_ANILIST,
                    op.malId,
                    status = saved.status ?: status.name,
                    episodes = saved.progress ?: op.progress,
                    score = saved.scoreRaw ?: 0,
                )
                log(sessionId, listOf(sent))
            } else if (lookUpNow) {
                log(sessionId, listOf(event(SyncEventKind.NOT_FOUND, op.malId)))
            }
        }
        dao.deleteOp(op.id)
    }

    private suspend fun resolveLocked(conflict: AniListConflict, keep: ListSide, sessionId: Long) {
        val format = store.currentInfo().account?.scoreFormat ?: ScoreFormat.POINT_10
        val mal = conflict.mal
        val aniList = conflict.aniList
        val entry = dao.entry(conflict.malId)
        when (keep) {
            ListSide.MAL -> {
                if (mal == null) {
                    enqueueLocked(conflict.malId, null, 0, null, sessionId)
                } else {
                    val write = Reconciler.toAniList(mal, aniList, format)
                    enqueueLocked(conflict.malId, write.status, write.progress, write.scoreRaw, sessionId)
                }
                entry?.let { dao.upsertEntry(it.withBase(mal, aniList)) }
            }
            ListSide.ANILIST -> {
                val newMal = writeMal(conflict.malId, aniList?.let { Reconciler.toMal(it, mal, format) })
                log(sessionId, listOf(malEvent(conflict.malId, newMal, System.currentTimeMillis())))
                entry?.let { dao.upsertEntry(it.withBase(newMal, aniList)) }
            }
        }
        dao.deleteConflict(conflict.malId)
    }

    /**
     * A change for MAL, as a swipe makes it: on the local list now, to MAL through its [Outbox].
     * [write] null removes the anime. Returns the anime as the MAL list has it now.
     */
    private suspend fun writeMal(malId: Int, write: MalWrite?): MalSide? {
        val listDao = database.listDao()
        if (write == null) {
            listDao.delete(malId)
            malOutbox.enqueue(malId, null, 0, null)
            return null
        }
        // AniList completes with its own count of episodes, MAL with its own
        val total = dao.anime(listOf(malId)).firstOrNull()?.episodes ?: 0
        val episodes = if (write.status == ListStatus.COMPLETED && total > 0) total else write.episodes
        val score = write.score ?: listDao.get(malId)?.score ?: 0
        listDao.upsert(ListEntry(malId, write.status, episodes, score))
        malOutbox.enqueue(malId, write.status, episodes, write.score)
        return MalSide(write.status, episodes, score)
    }

    /** A change made in the app (a swipe), in the app's session. */
    private suspend fun enqueue(malId: Int, status: AniListStatus?, progress: Int, scoreRaw: Int?) {
        mutex.withLock {
            database.withTransaction { enqueueLocked(malId, status, progress, scoreRaw, appSessionId()) }
        }
        // The change it replaced may have been the last one of its session
        logDao.closeFinished(System.currentTimeMillis())
        scheduleFlush()
    }

    /** The last change wins: one not sent yet for the same anime is dropped. */
    private suspend fun enqueueLocked(malId: Int, status: AniListStatus?, progress: Int, scoreRaw: Int?, sessionId: Long) {
        dao.deleteOpsFor(malId)
        dao.insertOp(
            AniListOp(
                malId = malId,
                status = status,
                progress = progress,
                scoreRaw = scoreRaw,
                createdAt = System.currentTimeMillis(),
                sessionId = sessionId,
            )
        )
    }

    private fun scheduleFlush() {
        val request = OneTimeWorkRequestBuilder<AniListOutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Also from the sync or the flush themselves: they aren't cancelled before the link is gone. */
    private suspend fun drop(expired: Boolean) {
        val current = currentCoroutineContext()[Job]
        syncJob?.takeIf { it != current }?.cancel()
        clearTables()
        store.unlink(expired)
        _state.value = AniListSyncState.Idle
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private suspend fun clearTables() {
        mutex.withLock {
            database.withTransaction {
                dao.clearOps()
                dao.clearConflicts()
                dao.clearEntries()
                logDao.clearSessions()
                logDao.clearEvents()
                logDao.clearAcknowledged()
            }
        }
    }

    private fun AniListEntry?.needsLookup(now: Long): Boolean =
        this == null || (mediaId == null && now - checkedAt > RECHECK_MS)

    private fun ListEntry.side() = MalSide(status, episodesWatched, score)

    private fun AniListListEntry.side(): AniListSide? =
        AniListStatus.fromApi(status)?.let { AniListSide(it, progress ?: 0, scoreRaw ?: 0) }

    private fun SavedEntry.side(): AniListSide? =
        AniListStatus.fromApi(status)?.let { AniListSide(it, progress ?: 0, scoreRaw ?: 0) }

    private fun AniListListEntry?.title(): String? = this?.media?.title?.romaji

    companion object {
        private const val WORK_NAME = "anilist_outbox"
        /** A login answered after this long isn't ours any more. */
        private const val LOGIN_MAX_AGE_MS = 60 * 60 * 1000L
        /** An anime AniList hadn't is looked up again after a week: it may have been added. */
        private const val RECHECK_MS = 7 * 24 * 60 * 60 * 1000L
        /** The sessions the log keeps. */
        const val KEPT_SESSIONS = 30
        /** Changes made in the app this far apart start a new session. */
        private const val APP_SESSION_GAP_MS = 30 * 60 * 1000L
        /** Anime a query, under SQLite's limit of parameters. */
        private const val TITLES_PAGE = 500
    }
}

class AniListOutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MitayoApp).container
        return when (container.aniListSync.flush()) {
            FlushResult.DONE -> Result.success()
            FlushResult.RETRY -> Result.retry()
            FlushResult.NOT_LOGGED_IN -> Result.failure()
        }
    }
}
