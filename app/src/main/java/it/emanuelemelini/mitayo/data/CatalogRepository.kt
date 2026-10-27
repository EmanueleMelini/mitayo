package it.emanuelemelini.mitayo.data

import androidx.room.withTransaction
import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.db.AnimeGenre
import it.emanuelemelini.mitayo.data.db.AppDatabase
import it.emanuelemelini.mitayo.data.db.Genre
import it.emanuelemelini.mitayo.data.db.RemoteListItem
import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.mal.MalApi
import it.emanuelemelini.mitayo.data.mal.MalNode
import it.emanuelemelini.mitayo.data.mal.MalPage
import it.emanuelemelini.mitayo.data.mal.MalUserAnime
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.mal.MalHttpException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.max

/**
 * Local catalog: MAL has no "anime by genre" endpoint, so the most popular anime are
 * downloaded once (with their genres) and every category is a local query on them.
 * [onPicturesRemoved]: posters of the anime taken out of the catalog, to drop from the image cache.
 */
class CatalogRepository(
    private val database: AppDatabase,
    private val api: MalApi,
    private val settingsRepository: SettingsRepository,
    private val onPicturesRemoved: (List<String>) -> Unit,
) {
    private val animeDao = database.animeDao()
    private val loadMutex = Mutex()

    /**
     * First download, then the daily pass over every page downloaded so far: it updates scores
     * and airing status, and removes what MAL doesn't list any more (the API agreement asks for
     * removed content to go within 24 hours). [onProgress]: anime downloaded so far, of the total.
     */
    suspend fun refresh(onProgress: (loaded: Int, total: Int) -> Unit) = loadMutex.withLock {
        val passStart = System.currentTimeMillis()
        // Other users' decks are downloaded again at every opening: none is kept past a day
        animeDao.clearUserLists()
        val total = max(INITIAL_SIZE, settingsRepository.currentSyncInfo().catalogNextOffset)
        var offset = 0
        var reachedEnd = false
        while (offset < total) {
            val page = api.popularity(offset)
            save(page.data.map { it.node }, seenAt = passStart)
            offset += page.data.size
            onProgress(offset, total)
            if (page.paging.next == null || page.data.isEmpty()) {
                reachedEnd = true
                break
            }
            delay(PAGE_INTERVAL_MS)
        }
        // Only after a complete pass: an interrupted one would remove what it didn't reach
        removeNotSeenSince(passStart)
        settingsRepository.setCatalogProgress(nextOffset = offset, complete = reachedEnd, syncedAt = passStart)
    }

    /** One more page by popularity, when a deck is running out. false: there's nothing more. */
    suspend fun loadMore(): Boolean = loadMutex.withLock {
        val info = settingsRepository.currentSyncInfo()
        if (info.catalogComplete) return false
        val page = api.popularity(info.catalogNextOffset)
        save(page.data.map { it.node })
        settingsRepository.setCatalogProgress(
            nextOffset = info.catalogNextOffset + page.data.size,
            complete = page.paging.next == null || page.data.isEmpty(),
            syncedAt = info.catalogSyncedAt,
        )
        page.data.isNotEmpty()
    }

    /** The anime of the user's own list: they stay in the catalog as long as they're in the list. */
    suspend fun saveListAnime(anime: List<MalAnime>) = save(anime)

    /**
     * Another user's list as a deck: their anime join the catalog, in their order (completed and
     * watching first, the best scored first). The previous user's deck goes: one at a time.
     */
    suspend fun saveUserList(category: Category.OfUser, entries: List<MalUserAnime>) {
        val ordered = entries.sortedWith(
            compareBy<MalUserAnime>(
                { USER_STATUS_ORDER.indexOf(it.listStatus.status).takeIf { index -> index >= 0 } ?: USER_STATUS_ORDER.size },
                { -it.listStatus.score },
                { it.node.popularity?.takeIf { popularity -> popularity > 0 } ?: Int.MAX_VALUE },
            )
        )
        save(ordered.map { it.node })
        database.withTransaction {
            animeDao.clearUserLists()
            animeDao.insertRemoteList(ordered.mapIndexed { index, entry -> RemoteListItem(category.key, entry.node.id, index) })
        }
    }

    suspend fun remoteListIsStale(category: Category): Boolean {
        if (category is Category.OfUser) return true
        val age = System.currentTimeMillis() - settingsRepository.remoteListRefreshedAt(category.key)
        return age > REMOTE_MAX_AGE_MS || animeDao.remoteListSize(category.key) == 0
    }

    /** Downloads a list that comes from MAL as is (seasons, upcoming, suggestions) and keeps its order. */
    suspend fun refreshRemoteList(category: Category) {
        val anime = when (category) {
            Category.Season -> {
                val season = AnimeSeason.current()
                pages(REMOTE_MAX_PAGES) { offset -> api.season(season.year, season.season, offset) }
            }
            is Category.OfSeason -> pages(REMOTE_MAX_PAGES) { offset -> api.season(category.year, category.season, offset) }
            Category.Upcoming -> pages(REMOTE_MAX_PAGES) { offset -> api.upcoming(offset) }
            Category.Suggested -> pages(REMOTE_MAX_PAGES) { offset -> api.suggestions(offset) }
            else -> return
        }.distinctBy { it.id }
        save(anime)
        database.withTransaction {
            if (category is Category.OfSeason) animeDao.clearOtherSeasons(except = category.key)
            animeDao.clearRemoteList(category.key)
            animeDao.insertRemoteList(anime.mapIndexed { index, item -> RemoteListItem(category.key, item.id, index) })
        }
        settingsRepository.setRemoteListRefreshedAt(category.key, System.currentTimeMillis())
    }

    /**
     * Searches MAL (only when the user asks: one request) and adds what it finds to the catalog,
     * where the local search shows it. Returns how many anime MAL found. Anime outside the
     * downloaded pages leave the catalog with the next daily pass, the user's list entries stay.
     */
    suspend fun searchMal(query: String): Int {
        val found = api.search(query).data.map { it.node }.distinctBy { it.id }
        save(found)
        return found.size
    }

    /** The anime from the catalog, or from MAL (and then in the catalog) when it isn't there. */
    suspend fun anime(animeId: Int): Anime {
        animeDao.get(animeId)?.let { return it }
        details(animeId)
        return checkNotNull(animeDao.get(animeId)) { "Anime $animeId not saved" }
    }

    /** A 404 means MAL deleted the anime: it leaves the device right away. */
    suspend fun details(animeId: Int): MalAnime = try {
        api.details(animeId).also { save(listOf(it)) }
    } catch (e: MalHttpException) {
        if (e.code == 404) removeAnime(animeId)
        throw e
    }

    private suspend fun removeNotSeenSince(passStart: Long) {
        val pictures = animeDao.picturesNotSeenSince(passStart)
        database.withTransaction {
            animeDao.deleteGenreLinksNotSeenSince(passStart)
            animeDao.deleteNotSeenSince(passStart)
        }
        if (pictures.isNotEmpty()) onPicturesRemoved(pictures)
    }

    /** Skipped ids stay: only an id, and the anime must not come back if it reappears. */
    private suspend fun removeAnime(animeId: Int) {
        val picture = animeDao.get(animeId)?.pictureUrl
        database.withTransaction {
            animeDao.deleteGenreLinks(listOf(animeId))
            animeDao.deleteFromRemoteLists(animeId)
            animeDao.delete(animeId)
        }
        if (picture != null) onPicturesRemoved(listOf(picture))
    }

    private suspend fun pages(
        maxPages: Int,
        load: suspend (offset: Int) -> MalPage<MalNode>,
    ): List<MalAnime> {
        val anime = mutableListOf<MalAnime>()
        repeat(maxPages) {
            val page = load(anime.size)
            page.data.mapTo(anime) { it.node }
            if (page.paging.next == null || page.data.isEmpty()) return anime
        }
        return anime
    }

    /** Saves anime from any MAL list, with their genres; [seenAt]: when MAL returned them. */
    private suspend fun save(anime: List<MalAnime>, seenAt: Long = System.currentTimeMillis()) {
        if (anime.isEmpty()) return
        database.withTransaction {
            animeDao.upsert(anime.map { it.toAnime(seenAt) })
            animeDao.upsertGenres(anime.flatMap { it.genres }.distinctBy { it.id }.map { Genre(it.id, it.name) })
            animeDao.deleteGenreLinks(anime.map { it.id })
            animeDao.insertGenreLinks(anime.flatMap { item -> item.genres.map { AnimeGenre(item.id, it.id) } })
        }
    }


    companion object {
        /** About 10 requests: enough for every anime most people have heard of. */
        const val INITIAL_SIZE = 5_000
        private const val REMOTE_MAX_AGE_MS = 12 * 60 * 60 * 1000L
        private const val REMOTE_MAX_PAGES = 2
        /** Another user's deck: what they've seen first. */
        private val USER_STATUS_ORDER = listOf("completed", "watching", "on_hold", "dropped", "plan_to_watch")

        /** Pause between the pages of a pass: MAL answers 403 to bursts. */
        private const val PAGE_INTERVAL_MS = 300L
    }
}

/** A MAL anime as the catalog keeps it; [seenAt]: when MAL returned it. */
fun MalAnime.toAnime(seenAt: Long = System.currentTimeMillis()) = Anime(
    id = id,
    title = title,
    titleEn = alternativeTitles?.en?.takeIf { it.isNotBlank() },
    titleJa = alternativeTitles?.ja?.takeIf { it.isNotBlank() },
    pictureUrl = mainPicture?.large ?: mainPicture?.medium,
    mediaType = mediaType,
    airingStatus = status,
    episodes = numEpisodes,
    mean = mean,
    popularity = popularity?.takeIf { it > 0 } ?: Int.MAX_VALUE,
    members = numListUsers ?: 0,
    seasonYear = startSeason?.year,
    season = startSeason?.season,
    nsfw = nsfw == "black",
    genreNames = genres.joinToString(", ") { it.name },
    lastSeenAt = seenAt,
    broadcastDay = broadcast?.dayOfTheWeek?.takeIf { it.isNotBlank() },
    broadcastTime = broadcast?.startTime?.takeIf { it.isNotBlank() },
)
