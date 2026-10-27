package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.db.AppDatabase
import it.emanuelemelini.mitayo.data.db.ListEntry
import it.emanuelemelini.mitayo.data.db.SwipeAction
import it.emanuelemelini.mitayo.data.db.SwipeRecord
import it.emanuelemelini.mitayo.data.db.SwipeRecordCard
import kotlinx.coroutines.flow.Flow

/**
 * The last swipes of the decks, kept on the phone: one can be undone later, also after leaving
 * the deck (its own undo lasts only while the deck is open).
 */
class SwipeHistory(
    database: AppDatabase,
    private val listRepository: ListRepository,
    private val deckRepository: DeckRepository,
) {
    private val dao = database.historyDao()

    /** The last swipe first. */
    val records: Flow<List<SwipeRecordCard>> = dao.observe()

    /**
     * [previous]: the list entry the swipe replaced. Returns the record id: the deck drops it
     * when the swipe is undone there.
     */
    suspend fun record(animeId: Int, action: SwipeAction, episodes: Int, score: Int, previous: ListEntry?): Long {
        val id = dao.insert(
            SwipeRecord(
                animeId = animeId,
                action = action,
                episodes = episodes,
                score = score,
                previousStatus = previous?.status,
                previousEpisodes = previous?.episodesWatched ?: 0,
                previousScore = previous?.score ?: 0,
                at = System.currentTimeMillis(),
            )
        )
        dao.trim(MAX_RECORDS)
        return id
    }

    /** Undone in the deck: it isn't in the history any more. */
    suspend fun forget(id: Long) = dao.delete(id)

    /** The anime goes back to how it was before that swipe, on MAL too. */
    suspend fun undo(record: SwipeRecord) {
        if (record.action == SwipeAction.SKIPPED) {
            deckRepository.unskip(record.animeId)
        } else {
            listRepository.restore(record.animeId, record.previous)
        }
        dao.delete(record.id)
    }

    companion object {
        const val MAX_RECORDS = 100
    }
}
