package it.emanuelemelini.mitayo.ui

import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.UserListUnavailableException
import it.emanuelemelini.mitayo.data.anilist.AniListHttpException
import it.emanuelemelini.mitayo.data.anilist.AniListUnauthorizedException
import it.emanuelemelini.mitayo.data.mal.MalHttpException
import it.emanuelemelini.mitayo.data.mal.NotLoggedInException
import java.io.IOException

/** A message the user can act on, never the raw exception. */
/** The error of an AniList sync kept in the log by its code ([errorCode][it.emanuelemelini.mitayo.data.anilist.errorCode]). */
fun aniListErrorText(code: Int): UiText = when (code) {
    -1 -> IOException().toUiText()
    0 -> Exception().toUiText()
    else -> AniListHttpException(code).toUiText()
}

fun Throwable.toUiText(): UiText = when (this) {
    is NotLoggedInException -> uiText(R.string.error_session_expired)
    is UserListUnavailableException ->
        if (notFound) uiText(R.string.error_user_not_found, userName) else uiText(R.string.error_user_list_private, userName)
    is MalHttpException -> when {
        code == 403 || code == 429 -> uiText(R.string.error_rate_limited)
        code == 404 -> uiText(R.string.error_anime_removed)
        code >= 500 -> uiText(R.string.error_mal_down)
        else -> uiText(R.string.error_mal_code, code)
    }
    is AniListUnauthorizedException -> uiText(R.string.anilist_expired)
    is AniListHttpException -> when {
        code == 403 || code == 429 -> uiText(R.string.anilist_error_busy)
        code >= 500 -> uiText(R.string.anilist_error_down)
        else -> uiText(R.string.anilist_error_code, code)
    }
    is IOException -> uiText(R.string.error_network)
    else -> uiText(R.string.error_generic)
}
