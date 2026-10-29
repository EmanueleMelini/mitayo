package it.emanuelemelini.mitayo.data.anilist

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Where the changes of a session come from. */
enum class SyncSessionKind {
    /** A sync of the two lists, and the changes it sends to AniList. */
    SYNC,

    /** Changes made in the app (swipes, conflict choices), a half hour of them together. */
    APP,
}

enum class SyncSessionState { RUNNING, DONE, FAILED }

enum class SyncEventKind {
    /** The AniList list downloaded; [AniListSessionEvent.count] anime. */
    LIST_READ,

    /** [AniListSessionEvent.count] anime of the MAL list looked up on AniList. */
    LOOKED_UP,
    TO_ANILIST,
    REMOVED_FROM_ANILIST,
    TO_MAL,
    REMOVED_FROM_MAL,

    /** An anime that AniList hasn't: it goes to the "Not on AniList" page. */
    NOT_FOUND,

    /** An anime of the "Not on AniList" page that AniList has now. */
    FOUND,
    CONFLICT,

    /** A change AniList refused ([AniListSessionEvent.code]): it isn't sent again. */
    SEND_FAILED,

    /** AniList busy or no connection ([AniListSessionEvent.code], -1 the connection): the sending waits. */
    WAITING,

    /** The sync stopped: [AniListSessionEvent.code] as in [errorCode]. */
    ERROR,
}

/**
 * A sync with AniList that changed something (or stopped), or a half hour of changes made in the
 * app: the last [AniListSync.KEPT_SESSIONS] stay, on this phone only.
 */
@Entity(tableName = "anilist_sessions")
data class AniListSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SyncSessionKind,
    val state: SyncSessionState,
    val startedAt: Long,
    /** The last event. */
    val updatedAt: Long,
    val endedAt: Long? = null,
    /** The in-app notices already shown: 0 none, [NOTICE_START] the start, [NOTICE_END] the end. */
    val notice: Int = 0,
) {
    companion object {
        const val NOTICE_START = 1
        const val NOTICE_END = 2
    }
}

/** One step of a session; [status] is AniList's or MAL's as [kind] says, [score] 0-100 or 0-10 the same way. */
@Entity(tableName = "anilist_session_events", indices = [Index("sessionId")])
data class AniListSessionEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val at: Long,
    val kind: SyncEventKind,
    val malId: Int? = null,
    /** The title when it happened: the anime may not be in the catalog later. */
    val title: String? = null,
    val status: String? = null,
    val episodes: Int = 0,
    val score: Int = 0,
    val code: Int? = null,
    val count: Int = 0,
)

/** Got it: an anime that stays on MAL only, out of the way on the "Not on AniList" page. */
@Entity(tableName = "anilist_acknowledged")
data class AniListAcknowledged(@PrimaryKey val malId: Int, val at: Long)

/** A session with what it did so far; [remaining] changes still to send. */
data class AniListSessionSummary(
    @Embedded val session: AniListSession,
    val toAniList: Int,
    val toMal: Int,
    val notFound: Int,
    val found: Int,
    /** The first anime found, to name it when it's the only one. */
    val foundTitle: String?,
    val conflicts: Int,
    val failed: Int,
    val remaining: Int,
    val errorCode: Int?,
)

/** The code an [ERROR][SyncEventKind.ERROR] keeps of [error]: the HTTP status, -1 no connection, 0 anything else. */
fun errorCode(error: Throwable): Int = when (error) {
    is AniListHttpException -> error.code
    is java.io.IOException -> -1
    else -> 0
}

/** A session with the counts of its events and of its changes still to send. */
private const val SUMMARY = """
    SELECT s.*,
        (SELECT COUNT(*) FROM anilist_session_events e WHERE e.sessionId = s.id
            AND e.kind IN ('TO_ANILIST', 'REMOVED_FROM_ANILIST')) AS toAniList,
        (SELECT COUNT(*) FROM anilist_session_events e WHERE e.sessionId = s.id
            AND e.kind IN ('TO_MAL', 'REMOVED_FROM_MAL')) AS toMal,
        (SELECT COUNT(*) FROM anilist_session_events e WHERE e.sessionId = s.id AND e.kind = 'NOT_FOUND') AS notFound,
        (SELECT COUNT(*) FROM anilist_session_events e WHERE e.sessionId = s.id AND e.kind = 'FOUND') AS found,
        (SELECT e.title FROM anilist_session_events e WHERE e.sessionId = s.id AND e.kind = 'FOUND'
            ORDER BY e.id LIMIT 1) AS foundTitle,
        (SELECT COUNT(*) FROM anilist_session_events e WHERE e.sessionId = s.id AND e.kind = 'CONFLICT') AS conflicts,
        (SELECT COUNT(*) FROM anilist_session_events e WHERE e.sessionId = s.id AND e.kind = 'SEND_FAILED') AS failed,
        (SELECT COUNT(*) FROM anilist_outbox o WHERE o.sessionId = s.id) AS remaining,
        (SELECT e.code FROM anilist_session_events e WHERE e.sessionId = s.id AND e.kind = 'ERROR'
            ORDER BY e.id DESC LIMIT 1) AS errorCode
    FROM anilist_sessions s
"""

@Dao
interface AniListLogDao {
    @Insert
    suspend fun insertSession(session: AniListSession): Long

    @Insert
    suspend fun insertEvents(events: List<AniListSessionEvent>)

    @Query("DELETE FROM anilist_sessions WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM anilist_session_events WHERE sessionId = :sessionId ORDER BY id DESC LIMIT 1")
    suspend fun lastEvent(sessionId: Long): AniListSessionEvent?

    @Query("UPDATE anilist_sessions SET updatedAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("UPDATE anilist_sessions SET state = :state, updatedAt = :at, endedAt = :endedAt WHERE id = :id")
    suspend fun setState(id: Long, state: SyncSessionState, at: Long, endedAt: Long?)

    /** The running sessions with nothing left to send are done. */
    @Query(
        """
        UPDATE anilist_sessions SET state = 'DONE', endedAt = :at, updatedAt = :at
        WHERE state = 'RUNNING' AND id NOT IN (SELECT sessionId FROM anilist_outbox WHERE sessionId IS NOT NULL)
        """
    )
    suspend fun closeFinished(at: Long)

    @Query("SELECT * FROM anilist_sessions WHERE kind = 'APP' AND updatedAt > :since ORDER BY id DESC LIMIT 1")
    suspend fun recentAppSession(since: Long): AniListSession?

    @Query("UPDATE anilist_sessions SET notice = :notice WHERE id = :id AND notice < :notice")
    suspend fun setNotice(id: Long, notice: Int)

    /** Only the last [keep] sessions, the running ones always. */
    @Query(
        """
        DELETE FROM anilist_sessions WHERE state != 'RUNNING'
            AND id NOT IN (SELECT id FROM anilist_sessions ORDER BY id DESC LIMIT :keep)
        """
    )
    suspend fun trimSessions(keep: Int)

    @Query("DELETE FROM anilist_session_events WHERE sessionId NOT IN (SELECT id FROM anilist_sessions)")
    suspend fun deleteOrphanEvents()

    @Query("$SUMMARY ORDER BY s.id DESC")
    fun observeSessions(): Flow<List<AniListSessionSummary>>

    @Query("$SUMMARY WHERE s.id = :id")
    fun observeSession(id: Long): Flow<AniListSessionSummary?>

    /** The syncs with a notice still to show: the start of a running one, the end of a finished one. */
    @Query(
        """
        $SUMMARY WHERE s.kind = 'SYNC'
            AND ((s.state = 'RUNNING' AND s.notice < 1) OR (s.state != 'RUNNING' AND s.notice < 2))
        ORDER BY s.id
        """
    )
    fun observeUnnoticed(): Flow<List<AniListSessionSummary>>

    @Query("SELECT * FROM anilist_session_events WHERE sessionId = :sessionId ORDER BY id DESC")
    fun observeEvents(sessionId: Long): Flow<List<AniListSessionEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun acknowledge(acknowledged: AniListAcknowledged)

    @Query("DELETE FROM anilist_acknowledged WHERE malId = :malId")
    suspend fun unacknowledge(malId: Int)

    @Query("DELETE FROM anilist_acknowledged WHERE malId IN (:malIds)")
    suspend fun unacknowledge(malIds: List<Int>)

    @Query("DELETE FROM anilist_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM anilist_session_events")
    suspend fun clearEvents()

    @Query("DELETE FROM anilist_acknowledged")
    suspend fun clearAcknowledged()
}
