package it.emanuelemelini.mitayo.data.anilist

import android.net.Uri
import androidx.core.net.toUri
import java.net.URLDecoder

/**
 * AniList OAuth2 with the implicit grant, the one AniList suggests for mobile apps: no secret in
 * the app, the token comes back in the redirect mitayo://anilist#access_token=… (registered on
 * anilist.co/settings/developer). It lasts a year and can't be refreshed.
 */
object AniListLogin {

    sealed interface Redirect {
        data class Token(val token: String, val expiresInSeconds: Long) : Redirect

        data object Cancelled : Redirect

        data object Failed : Redirect
    }

    fun authorizeUri(clientId: String): Uri = AUTHORIZE_URL.toUri().buildUpon()
        .appendQueryParameter("client_id", clientId)
        .appendQueryParameter("response_type", "token")
        .build()

    /** From the [fragment] of the redirect (or its [error] parameter, when the user said no). */
    fun parseRedirect(fragment: String?, error: String?): Redirect {
        if (error != null) return if (error == "access_denied") Redirect.Cancelled else Redirect.Failed
        val values = fragment.orEmpty().split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size == 2) parts[0] to URLDecoder.decode(parts[1], "UTF-8") else null
        }.toMap()
        values["error"]?.let { return if (it == "access_denied") Redirect.Cancelled else Redirect.Failed }
        val token = values["access_token"]?.takeIf { it.isNotBlank() } ?: return Redirect.Failed
        val expiresIn = values["expires_in"]?.toLongOrNull()?.takeIf { it > 0 } ?: DEFAULT_LIFETIME_SECONDS
        return Redirect.Token(token, expiresIn)
    }

    const val REDIRECT_HOST = "anilist"
    private const val AUTHORIZE_URL = "https://anilist.co/api/v2/oauth/authorize"
    private const val DEFAULT_LIFETIME_SECONDS = 365L * 24 * 60 * 60
}
