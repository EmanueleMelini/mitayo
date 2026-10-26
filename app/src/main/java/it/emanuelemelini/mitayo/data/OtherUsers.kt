package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.mal.MalApi
import it.emanuelemelini.mitayo.data.mal.MalHttpException
import it.emanuelemelini.mitayo.data.mal.MalUserAnime
import kotlinx.coroutines.delay

/** MAL user names: 2 to 16 letters, digits, "_" or "-". */
object MalUserNames {
    private val PATTERN = Regex("^[A-Za-z0-9_-]{2,16}$")

    fun isValid(name: String): Boolean = PATTERN.matches(name)
}

/** MAL won't give [userName]'s list: no such user, or the list isn't public. */
class UserListUnavailableException(val userName: String, val notFound: Boolean) :
    Exception(if (notFound) "No MAL user $userName" else "List of $userName not available")

/**
 * Other users' lists, read only when the user asks (a comparison, another user's deck). They
 * stay in memory: MAL's agreement allows other users' data on the phone only for as long as needed.
 */
class OtherLists(private val api: MalApi) {

    /** [userName] null: the logged-in user. Every page, up to [MAX_PAGES]. */
    suspend fun list(userName: String?): List<MalUserAnime> {
        val path = userName ?: ME
        require(userName == null || MalUserNames.isValid(userName)) { "Invalid user name" }
        val entries = mutableListOf<MalUserAnime>()
        try {
            repeat(MAX_PAGES) { page ->
                if (page > 0) delay(PAGE_INTERVAL_MS)
                val result = api.userAnimeList(path, entries.size)
                entries += result.data
                if (result.paging.next == null || result.data.isEmpty()) return entries.distinctBy { it.node.id }
            }
        } catch (e: MalHttpException) {
            if (userName != null && (e.code == 404 || e.code == 403)) throw UserListUnavailableException(userName, notFound = e.code == 404)
            throw e
        }
        return entries.distinctBy { it.node.id }
    }

    private companion object {
        const val ME = "@me"
        /** 20,000 entries: far more than any list. */
        const val MAX_PAGES = 20
        /** MAL answers 403 to bursts. */
        const val PAGE_INTERVAL_MS = 300L
    }
}
