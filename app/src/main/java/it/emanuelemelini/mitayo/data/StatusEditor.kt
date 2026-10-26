package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.db.ListStatus

/**
 * A MAL status chosen from a list of anime (the skipped page, the search) instead of a swipe. The
 * anime also leaves the skipped ones: it's decided now, and the decks treat it like any other
 * anime of the list.
 */
class StatusEditor(private val deckRepository: DeckRepository, private val listRepository: ListRepository) {

    /** [episodes] for watching. Returns what puts everything back, on MAL too. */
    suspend fun setStatus(anime: Anime, status: ListStatus, episodes: Int = 0): suspend () -> Unit {
        val previous = when (status) {
            ListStatus.COMPLETED -> listRepository.markCompleted(anime)
            ListStatus.WATCHING -> listRepository.markWatching(anime.id, episodes)
            else -> listRepository.markStatus(anime.id, status)
        }
        val skippedAt = deckRepository.unskip(anime.id)
        return {
            listRepository.restore(anime.id, previous)
            if (skippedAt != null) deckRepository.skip(anime.id, skippedAt)
        }
    }
}
