package it.emanuelemelini.mitayo.data.anilist

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListEntry
import it.emanuelemelini.mitayo.data.db.ListStatus
import kotlinx.coroutines.flow.Flow

/**
 * An anime of the MAL list or of the AniList list: where it is on AniList and, once synced, how
 * both lists had it at the end of the last sync (the base of the next one).
 */
@Entity(tableName = "anilist_entries")
@TypeConverters(AniListConverters::class)
data class AniListEntry(
    @PrimaryKey val malId: Int,
    /** The anime on AniList; null: AniList hasn't it (looked up at [checkedAt]). */
    val mediaId: Int?,
    val checkedAt: Long,
    /** The entry of the user's AniList list, to remove it; null when it isn't in the list. */
    val entryId: Int? = null,
    /** false until a sync sees both lists say the same. */
    val synced: Boolean = false,
    val malStatus: ListStatus? = null,
    val malEpisodes: Int = 0,
    val malScore: Int = 0,
    val aniListStatus: AniListStatus? = null,
    val aniListProgress: Int = 0,
    val aniListScoreRaw: Int = 0,
) {
    val base: SyncBase?
        get() = if (!synced) null else SyncBase(malSide(malStatus, malEpisodes, malScore), aniListSide(aniListStatus, aniListProgress, aniListScoreRaw))

    fun withBase(mal: MalSide?, aniList: AniListSide?) = copy(
        synced = true,
        malStatus = mal?.status,
        malEpisodes = mal?.episodes ?: 0,
        malScore = mal?.score ?: 0,
        aniListStatus = aniList?.status,
        aniListProgress = aniList?.progress ?: 0,
        aniListScoreRaw = aniList?.scoreRaw ?: 0,
    )
}

/** An anime of the MAL list that AniList hasn't; [acknowledged]: the user said "got it". */
data class MissingAnime(@Embedded val card: DeckCard, val acknowledged: Boolean)

/** A change waiting to reach AniList: a swipe, a copy from MAL, a choice in a conflict. */
@Entity(tableName = "anilist_outbox")
@TypeConverters(AniListConverters::class)
data class AniListOp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val malId: Int,
    /** null: the anime leaves the AniList list. */
    val status: AniListStatus?,
    val progress: Int,
    /** 0-100; null: the score on AniList stays as it is. */
    val scoreRaw: Int?,
    val createdAt: Long,
    /** The [AniListSession] it belongs to; null for the changes saved before the sessions (v6). */
    val sessionId: Long? = null,
)

/** Two lists that don't agree on an anime, as they were when it was found. */
@Entity(tableName = "anilist_conflicts")
@TypeConverters(AniListConverters::class)
data class AniListConflict(
    @PrimaryKey val malId: Int,
    /** AniList's romaji title, for the anime not downloaded from MAL yet. */
    val title: String?,
    val malStatus: ListStatus?,
    val malEpisodes: Int,
    val malScore: Int,
    val aniListStatus: AniListStatus?,
    val aniListProgress: Int,
    val aniListScoreRaw: Int,
    val foundAt: Long,
) {
    val mal: MalSide? get() = malSide(malStatus, malEpisodes, malScore)
    val aniList: AniListSide? get() = aniListSide(aniListStatus, aniListProgress, aniListScoreRaw)

    companion object {
        fun of(malId: Int, title: String?, mal: MalSide?, aniList: AniListSide?, at: Long) = AniListConflict(
            malId = malId,
            title = title,
            malStatus = mal?.status,
            malEpisodes = mal?.episodes ?: 0,
            malScore = mal?.score ?: 0,
            aniListStatus = aniList?.status,
            aniListProgress = aniList?.progress ?: 0,
            aniListScoreRaw = aniList?.scoreRaw ?: 0,
            foundAt = at,
        )
    }
}

private fun malSide(status: ListStatus?, episodes: Int, score: Int) = status?.let { MalSide(it, episodes, score) }

private fun aniListSide(status: AniListStatus?, progress: Int, scoreRaw: Int) =
    status?.let { AniListSide(it, progress, scoreRaw) }

/** AniList statuses as AniList names them. */
class AniListConverters {
    @TypeConverter
    fun statusToString(status: AniListStatus?): String? = status?.name

    @TypeConverter
    fun stringToStatus(value: String?): AniListStatus? = AniListStatus.fromApi(value)
}

@Dao
interface AniListDao {
    /** The local copy of the MAL list, changes not sent yet included. */
    @Query("SELECT * FROM list_entries")
    suspend fun malList(): List<ListEntry>

    /** Anime with a change still to send to MAL. */
    @Query("SELECT DISTINCT animeId FROM outbox")
    suspend fun malPendingIds(): List<Int>

    @Query("SELECT * FROM anime WHERE id IN (:ids)")
    suspend fun anime(ids: List<Int>): List<Anime>

    /** The anime of the MAL list that AniList hasn't, as the last look-up found them. */
    @Query(
        """
        SELECT a.*, l.status AS listStatus, l.episodesWatched AS listEpisodes, l.score AS listScore,
            k.malId IS NOT NULL AS acknowledged
        FROM anilist_entries e
        JOIN list_entries l ON l.animeId = e.malId
        JOIN anime a ON a.id = e.malId
        LEFT JOIN anilist_acknowledged k ON k.malId = e.malId
        WHERE e.mediaId IS NULL
        ORDER BY a.title COLLATE NOCASE
        """
    )
    fun observeNotOnAniList(): Flow<List<MissingAnime>>

    @Query("SELECT malId FROM anilist_entries WHERE mediaId IS NULL")
    suspend fun notFoundIds(): List<Int>

    @Query("SELECT * FROM anilist_entries")
    suspend fun entries(): List<AniListEntry>

    @Query("SELECT * FROM anilist_entries WHERE malId = :malId")
    suspend fun entry(malId: Int): AniListEntry?

    @Upsert
    suspend fun upsertEntries(entries: List<AniListEntry>)

    @Upsert
    suspend fun upsertEntry(entry: AniListEntry)

    /** Oldest first: changes reach AniList in the order they were made. */
    @Query("SELECT * FROM anilist_outbox ORDER BY id LIMIT 1")
    suspend fun firstOp(): AniListOp?

    @Query("SELECT DISTINCT malId FROM anilist_outbox")
    suspend fun pendingIds(): List<Int>

    @Insert
    suspend fun insertOp(op: AniListOp): Long

    @Query("DELETE FROM anilist_outbox WHERE malId = :malId")
    suspend fun deleteOpsFor(malId: Int)

    @Query("DELETE FROM anilist_outbox WHERE id = :id")
    suspend fun deleteOp(id: Long)

    @Query("SELECT COUNT(*) FROM anilist_outbox")
    fun observeOpCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM anilist_outbox WHERE sessionId = :sessionId")
    suspend fun opCount(sessionId: Long): Int

    @Query("SELECT * FROM anilist_conflicts ORDER BY foundAt, malId")
    fun observeConflicts(): Flow<List<AniListConflict>>

    @Query("SELECT COUNT(*) FROM anilist_conflicts")
    fun observeConflictCount(): Flow<Int>

    @Query("SELECT * FROM anilist_conflicts")
    suspend fun conflicts(): List<AniListConflict>

    @Query("SELECT * FROM anilist_conflicts WHERE malId = :malId")
    suspend fun conflict(malId: Int): AniListConflict?

    @Upsert
    suspend fun upsertConflict(conflict: AniListConflict)

    @Query("DELETE FROM anilist_conflicts WHERE malId = :malId")
    suspend fun deleteConflict(malId: Int)

    @Query("DELETE FROM anilist_entries")
    suspend fun clearEntries()

    /** Forgets the anime not found on AniList: the next sync looks them up again. */
    @Query("DELETE FROM anilist_entries WHERE mediaId IS NULL")
    suspend fun clearNotFound()

    @Query("DELETE FROM anilist_outbox")
    suspend fun clearOps()

    @Query("DELETE FROM anilist_conflicts")
    suspend fun clearConflicts()
}
