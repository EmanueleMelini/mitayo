package it.emanuelemelini.mitayo.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SupportSQLiteQuery
import it.emanuelemelini.mitayo.data.Category
import kotlinx.coroutines.flow.Flow

/** The daily pass keeps what a remote list or the user's own list still holds. */
private const val NOT_SEEN_SINCE =
    "lastSeenAt < :time AND id NOT IN (SELECT animeId FROM remote_lists) AND id NOT IN (SELECT animeId FROM list_entries)"

@Dao
interface AnimeDao {
    @Upsert
    suspend fun upsert(anime: List<Anime>)

    /**
     * Anime of the catalog with [pattern] (a LIKE pattern, escaped with \) in any title, the most
     * popular first. Completed and skipped ones too: the search is for any anime.
     */
    @Query(
        """
        SELECT a.*, l.status AS listStatus, l.episodesWatched AS listEpisodes, l.score AS listScore,
            s.animeId IS NOT NULL AS skipped
        FROM anime a
        LEFT JOIN list_entries l ON l.animeId = a.id
        LEFT JOIN skipped s ON s.animeId = a.id
        WHERE (a.title LIKE :pattern ESCAPE '\' OR a.titleEn LIKE :pattern ESCAPE '\' OR a.titleJa LIKE :pattern ESCAPE '\')
            AND (:showNsfw OR a.nsfw = 0)
        ORDER BY CASE WHEN a.popularity > 0 THEN a.popularity ELSE 1000000000 END, a.id
        LIMIT :limit
        """
    )
    fun search(pattern: String, showNsfw: Boolean, limit: Int): Flow<List<CatalogMatch>>

    @Upsert
    suspend fun upsertGenres(genres: List<Genre>)

    @Query("DELETE FROM anime_genres WHERE animeId IN (:animeIds)")
    suspend fun deleteGenreLinks(animeIds: List<Int>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGenreLinks(links: List<AnimeGenre>)

    @Query("SELECT * FROM anime WHERE id = :id")
    suspend fun get(id: Int): Anime?

    @Query("SELECT name FROM genres WHERE id = :genreId")
    suspend fun genreName(genreId: Int): String?

    @Query("DELETE FROM anime WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM remote_lists WHERE animeId = :animeId")
    suspend fun deleteFromRemoteLists(animeId: Int)

    @Query("SELECT pictureUrl FROM anime WHERE pictureUrl IS NOT NULL AND $NOT_SEEN_SINCE")
    suspend fun picturesNotSeenSince(time: Long): List<String>

    @Query("DELETE FROM anime_genres WHERE animeId IN (SELECT id FROM anime WHERE $NOT_SEEN_SINCE)")
    suspend fun deleteGenreLinksNotSeenSince(time: Long)

    /** Not seen since [time], unless a remote list still holds it. */
    @Query("DELETE FROM anime WHERE $NOT_SEEN_SINCE")
    suspend fun deleteNotSeenSince(time: Long): Int

    @Query("DELETE FROM remote_lists WHERE listKey = :listKey")
    suspend fun clearRemoteList(listKey: String)

    /** Every other user's deck: their lists stay on the phone only while needed. */
    @Query("DELETE FROM remote_lists WHERE listKey LIKE '${Category.USER_PREFIX}%'")
    suspend fun clearUserLists()

    /** The other seasons: only the one opened last stays. */
    @Query("DELETE FROM remote_lists WHERE listKey LIKE '${Category.SEASON_PREFIX}%' AND listKey != :except")
    suspend fun clearOtherSeasons(except: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemoteList(items: List<RemoteListItem>)

    @Query("SELECT COUNT(*) FROM remote_lists WHERE listKey = :listKey")
    suspend fun remoteListSize(listKey: String): Int

    /** Built by DeckQuery: the tables it reads are listed so the deck updates after every swipe. */
    @RawQuery(observedEntities = [Anime::class, AnimeGenre::class, ListEntry::class, SkippedAnime::class, RemoteListItem::class])
    fun observeCards(query: SupportSQLiteQuery): Flow<List<DeckCard>>

    @RawQuery(observedEntities = [Anime::class, AnimeGenre::class, ListEntry::class, SkippedAnime::class, RemoteListItem::class])
    fun observeCount(query: SupportSQLiteQuery): Flow<Int>

    /** Same filter as the decks (not in the list, not skipped); finished genres stay, with 0. */
    @Query(
        """
        SELECT g.id AS id, g.name AS name,
            SUM(CASE WHEN l.status IS NULL
                AND a.id NOT IN (SELECT animeId FROM skipped) THEN 1 ELSE 0 END) AS remaining
        FROM genres g
        JOIN anime_genres ag ON ag.genreId = g.id
        JOIN anime a ON a.id = ag.animeId
        LEFT JOIN list_entries l ON l.animeId = a.id
        WHERE (:showNsfw OR a.nsfw = 0)
        GROUP BY g.id
        ORDER BY remaining DESC, g.name
        """
    )
    fun observeGenreCounts(showNsfw: Boolean): Flow<List<GenreCount>>

    /** Same filter as the decks, by the year the anime started; the newest decade first. */
    @Query(
        """
        SELECT CASE WHEN a.seasonYear < ${Category.OfDecade.FIRST} THEN ${Category.OfDecade.BEFORE}
                ELSE a.seasonYear / 10 * 10 END AS start,
            SUM(CASE WHEN l.status IS NULL
                AND a.id NOT IN (SELECT animeId FROM skipped) THEN 1 ELSE 0 END) AS remaining
        FROM anime a
        LEFT JOIN list_entries l ON l.animeId = a.id
        WHERE a.seasonYear IS NOT NULL AND (:showNsfw OR a.nsfw = 0)
        GROUP BY start
        ORDER BY start DESC
        """
    )
    fun observeDecadeCounts(showNsfw: Boolean): Flow<List<DecadeCount>>

    /**
     * A random anime of the plan to watch to start, among those already airing or aired; [exclude]:
     * the ones already proposed this time.
     */
    @Query(
        """
        SELECT a.*, l.status AS listStatus, l.episodesWatched AS listEpisodes, l.score AS listScore
        FROM list_entries l
        JOIN anime a ON a.id = l.animeId
        WHERE l.status = 'plan_to_watch' AND (a.airingStatus IS NULL OR a.airingStatus != 'not_yet_aired')
            AND a.id NOT IN (SELECT animeId FROM skipped) AND (:showNsfw OR a.nsfw = 0) AND a.id NOT IN (:exclude)
        ORDER BY RANDOM()
        LIMIT 1
        """
    )
    suspend fun randomToStart(showNsfw: Boolean, exclude: List<Int>): DeckCard?

    /** The anime being watched that are still airing with a weekly slot, for the new episode reminder. */
    @Query(
        """
        SELECT a.* FROM list_entries l
        JOIN anime a ON a.id = l.animeId
        WHERE l.status = 'watching' AND a.airingStatus = 'currently_airing'
            AND a.broadcastDay IS NOT NULL AND a.broadcastTime IS NOT NULL
        ORDER BY a.popularity
        """
    )
    suspend fun watchingOnAir(): List<Anime>

    /** The decks by status: how many anime of the list have each status (skipped ones aside). */
    @Query(
        """
        SELECT l.status AS status, COUNT(*) AS remaining
        FROM list_entries l
        JOIN anime a ON a.id = l.animeId
        WHERE a.id NOT IN (SELECT animeId FROM skipped) AND (:showNsfw OR a.nsfw = 0)
        GROUP BY l.status
        """
    )
    fun observeStatusCounts(showNsfw: Boolean): Flow<List<StatusCount>>
}

@Dao
interface ListDao {
    @Query("SELECT * FROM list_entries WHERE animeId = :animeId")
    suspend fun get(animeId: Int): ListEntry?

    @Upsert
    suspend fun upsert(entry: ListEntry)

    @Upsert
    suspend fun upsertAll(entries: List<ListEntry>)

    @Query("DELETE FROM list_entries WHERE animeId = :animeId")
    suspend fun delete(animeId: Int)

    @Query("DELETE FROM list_entries")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM list_entries WHERE status = 'completed'")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT * FROM list_entries WHERE animeId IN (:animeIds)")
    fun observe(animeIds: List<Int>): Flow<List<ListEntry>>
}

@Dao
interface SkippedDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skipped: SkippedAnime)

    @Query("DELETE FROM skipped WHERE animeId = :animeId")
    suspend fun delete(animeId: Int)

    @Query("SELECT skippedAt FROM skipped WHERE animeId = :animeId")
    suspend fun skippedAt(animeId: Int): Long?

    @Query("SELECT * FROM skipped ORDER BY skippedAt")
    suspend fun all(): List<SkippedAnime>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(skipped: List<SkippedAnime>)

    @Query("DELETE FROM skipped")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM skipped")
    fun observeCount(): Flow<Int>

    @Query("SELECT animeId FROM skipped WHERE animeId IN (:animeIds)")
    fun observeIds(animeIds: List<Int>): Flow<List<Int>>

    /** Skipped anime still in the catalog, the last skipped first. */
    @Query(
        """
        SELECT a.*, l.status AS listStatus, l.episodesWatched AS listEpisodes, l.score AS listScore
        FROM skipped s
        JOIN anime a ON a.id = s.animeId
        LEFT JOIN list_entries l ON l.animeId = a.id
        ORDER BY s.skippedAt DESC
        """
    )
    fun observeCards(): Flow<List<DeckCard>>
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(record: SwipeRecord): Long

    @Query("DELETE FROM swipe_history WHERE id = :id")
    suspend fun delete(id: Long)

    /** Only the last [keep] records stay. */
    @Query("DELETE FROM swipe_history WHERE id NOT IN (SELECT id FROM swipe_history ORDER BY id DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query("DELETE FROM swipe_history")
    suspend fun clear()

    @Query("SELECT * FROM swipe_history WHERE id = :id")
    suspend fun get(id: Long): SwipeRecord?

    /** The last swipes first, with their anime (the ones still in the catalog). */
    @Query(
        """
        SELECT h.*, a.id AS anime_id, a.title AS anime_title, a.titleEn AS anime_titleEn, a.titleJa AS anime_titleJa,
            a.pictureUrl AS anime_pictureUrl, a.mediaType AS anime_mediaType, a.airingStatus AS anime_airingStatus,
            a.episodes AS anime_episodes, a.mean AS anime_mean, a.popularity AS anime_popularity,
            a.members AS anime_members, a.seasonYear AS anime_seasonYear, a.season AS anime_season,
            a.nsfw AS anime_nsfw, a.genreNames AS anime_genreNames, a.lastSeenAt AS anime_lastSeenAt,
            a.broadcastDay AS anime_broadcastDay, a.broadcastTime AS anime_broadcastTime
        FROM swipe_history h
        JOIN anime a ON a.id = h.animeId
        ORDER BY h.id DESC
        """
    )
    fun observe(): Flow<List<SwipeRecordCard>>
}

@Dao
interface OutboxDao {
    /** Oldest first: changes reach MAL in the order they were made. */
    @Query("SELECT * FROM outbox ORDER BY id LIMIT 1")
    suspend fun first(): OutboxOp?

    @Query("SELECT * FROM outbox ORDER BY id")
    suspend fun all(): List<OutboxOp>

    @Insert
    suspend fun insert(op: OutboxOp): Long

    @Query("DELETE FROM outbox WHERE animeId = :animeId")
    suspend fun deleteFor(animeId: Int)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM outbox")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM outbox")
    fun observeCount(): Flow<Int>
}
