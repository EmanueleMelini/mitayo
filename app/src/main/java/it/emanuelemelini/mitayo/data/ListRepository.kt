package it.emanuelemelini.mitayo.data

import androidx.room.withTransaction
import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.db.AppDatabase
import it.emanuelemelini.mitayo.data.db.ListEntry
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.mal.MalApi
import kotlinx.coroutines.flow.Flow

/** The user's MAL list: changed locally at once, sent to MAL through the [Outbox]. */
class ListRepository(
    private val database: AppDatabase,
    private val api: MalApi,
    private val outbox: Outbox,
) {
    private val listDao = database.listDao()

    val completedCount: Flow<Int> = listDao.observeCompletedCount()

    suspend fun entry(animeId: Int): ListEntry? = listDao.get(animeId)

    /** The entries of these anime that are in the list. */
    fun observeEntries(animeIds: List<Int>): Flow<List<ListEntry>> = listDao.observe(animeIds)

    /**
     * Replaces the local copy with the list on MAL; changes not sent yet stay on top of it.
     * Returns the anime of the list, for the catalog: the decks by status need every one of them.
     */
    suspend fun syncFromMal(): List<MalAnime> {
        val entries = mutableListOf<ListEntry>()
        val anime = mutableListOf<MalAnime>()
        var offset = 0
        while (true) {
            val page = api.userAnimeList(MY_LIST, offset)
            page.data.forEach { item ->
                ListStatus.fromApi(item.listStatus.status)?.let {
                    entries += ListEntry(item.node.id, it, item.listStatus.episodesWatched, item.listStatus.score)
                    anime += item.node
                }
            }
            if (page.paging.next == null || page.data.isEmpty()) break
            offset += page.data.size
        }
        database.withTransaction {
            listDao.clear()
            listDao.upsertAll(entries)
            outbox.pending().forEach { op -> applyLocally(op.animeId, op.status, op.episodesWatched, op.score) }
        }
        return anime
    }

    /** Swipe right: completed with every episode, and [score] if given. Returns the entry it replaced, for undo. */
    suspend fun markCompleted(anime: Anime, score: Int? = null): ListEntry? =
        setStatus(anime.id, ListStatus.COMPLETED, anime.episodes, score)

    suspend fun markWatching(animeId: Int, episodesWatched: Int): ListEntry? =
        setStatus(animeId, ListStatus.WATCHING, episodesWatched)

    /** Any other status, keeping the episodes already on MAL. Returns the entry it replaced. */
    suspend fun markStatus(animeId: Int, status: ListStatus): ListEntry? {
        val episodes = listDao.get(animeId)?.episodesWatched ?: 0
        return setStatus(animeId, status, episodes)
    }

    /** Only the score: status and episodes stay. null when the anime isn't in the list. */
    suspend fun markScore(animeId: Int, score: Int): ListEntry? {
        val current = listDao.get(animeId) ?: return null
        return setStatus(animeId, current.status, current.episodesWatched, score)
    }

    /**
     * Undo: puts back [previous] (null: the anime wasn't in the list, so it's removed), its score
     * too: a score given by the undone change goes away on MAL as well.
     */
    suspend fun restore(animeId: Int, previous: ListEntry?) {
        applyLocally(animeId, previous?.status, previous?.episodesWatched ?: 0, previous?.score)
        outbox.enqueue(animeId, previous?.status, previous?.episodesWatched ?: 0, previous?.score)
    }

    /** [score] null keeps the one already in the list. */
    private suspend fun setStatus(animeId: Int, status: ListStatus, episodesWatched: Int, score: Int? = null): ListEntry? {
        val previous = listDao.get(animeId)
        applyLocally(animeId, status, episodesWatched, score)
        outbox.enqueue(animeId, status, episodesWatched, score)
        return previous
    }

    private suspend fun applyLocally(animeId: Int, status: ListStatus?, episodesWatched: Int, score: Int?) {
        if (status == null) {
            listDao.delete(animeId)
        } else {
            val keptScore = score ?: listDao.get(animeId)?.score ?: 0
            listDao.upsert(ListEntry(animeId, status, episodesWatched, keptScore))
        }
    }

    private companion object {
        const val MY_LIST = "@me"
    }
}
