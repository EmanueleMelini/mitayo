package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.db.ListStatus

/**
 * Another list kept in step with the MAL one: AniList, when linked. [ListRepository] tells it every
 * change made in Mitayo, [SyncManager] when the MAL list has been synced.
 */
interface ListMirror {
    /** A change already on the local list and queued for MAL: [status] null removes the anime, [score] null keeps it. */
    suspend fun mirror(animeId: Int, status: ListStatus?, episodesWatched: Int, score: Int?)

    /** The MAL list was just synced: the other list can follow. */
    fun onListSynced()

    /** The MAL logout: nothing of the user stays, the link neither. */
    suspend fun clear()
}
