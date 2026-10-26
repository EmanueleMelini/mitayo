package it.emanuelemelini.mitayo.data

import androidx.room.withTransaction
import it.emanuelemelini.mitayo.data.db.AppDatabase
import it.emanuelemelini.mitayo.data.db.CatalogMatch
import it.emanuelemelini.mitayo.data.db.DecadeCount
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.GenreCount
import it.emanuelemelini.mitayo.data.db.SkippedAnime
import it.emanuelemelini.mitayo.data.db.StatusCount
import kotlinx.coroutines.flow.Flow

/** The decks to swipe and the skipped anime. */
class DeckRepository(private val database: AppDatabase) {
    private val animeDao = database.animeDao()
    private val skippedDao = database.skippedDao()

    val skippedCount: Flow<Int> = skippedDao.observeCount()

    val skipped: Flow<List<DeckCard>> = skippedDao.observeCards()

    /** The first [limit] cards; the flow updates after every swipe. */
    fun observeCards(category: Category, showNsfw: Boolean, limit: Int): Flow<List<DeckCard>> =
        animeDao.observeCards(DeckQuery.cards(category, showNsfw, limit))

    fun observeRemaining(category: Category, showNsfw: Boolean): Flow<Int> =
        animeDao.observeCount(DeckQuery.count(category, showNsfw))

    fun observeGenreCounts(showNsfw: Boolean): Flow<List<GenreCount>> = animeDao.observeGenreCounts(showNsfw)

    fun observeDecadeCounts(showNsfw: Boolean): Flow<List<DecadeCount>> = animeDao.observeDecadeCounts(showNsfw)

    fun observeStatusCounts(showNsfw: Boolean): Flow<List<StatusCount>> = animeDao.observeStatusCounts(showNsfw)

    suspend fun genreName(genreId: Int): String? = animeDao.genreName(genreId)

    /** Catalog anime with [text] in any of their titles, case-insensitive (for Latin letters). */
    fun search(text: String, showNsfw: Boolean): Flow<List<CatalogMatch>> {
        val escaped = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return animeDao.search("%$escaped%", showNsfw, SEARCH_LIMIT)
    }

    /** Swipe left: hidden for good, until [restoreSkipped]. [at]: an undo puts back the original time. */
    suspend fun skip(animeId: Int, at: Long = System.currentTimeMillis()) {
        skippedDao.insert(SkippedAnime(animeId, at))
    }

    /** Returns when the anime had been skipped (null: it wasn't), to undo it in its place. */
    suspend fun unskip(animeId: Int): Long? = database.withTransaction {
        skippedDao.skippedAt(animeId).also { skippedDao.delete(animeId) }
    }

    suspend fun restoreSkipped() {
        skippedDao.clear()
    }

    companion object {
        /** More than this and the user should type a bit more. */
        const val SEARCH_LIMIT = 50
    }
}
