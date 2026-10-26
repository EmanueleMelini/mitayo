package it.emanuelemelini.mitayo.data.mal

/**
 * No valid MAL session (never logged in, or the refresh token expired): the user must log in
 * again. Not an IOException, so callers don't mistake it for a network problem to retry.
 */
class NotLoggedInException : Exception("Not logged in to MyAnimeList")

/**
 * MAL answered with an error status ([code]). 403 also means "too many requests" on MAL. The
 * message keeps only MAL's error code ("not_found"), never the body: it may end up in a crash report.
 */
class MalHttpException(val code: Int, body: String) : Exception("MAL HTTP $code${errorCode(body)?.let { " ($it)" }.orEmpty()}")

private val ERROR_CODE = Regex(""""error"\s*:\s*"([a-z_]{1,40})"""")

private fun errorCode(body: String): String? = ERROR_CODE.find(body)?.groupValues?.get(1)
