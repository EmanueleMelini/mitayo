package it.emanuelemelini.mitayo.data.auth

import android.net.Uri
import androidx.core.net.toUri
import it.emanuelemelini.mitayo.BuildConfig
import it.emanuelemelini.mitayo.data.http.awaitText
import it.emanuelemelini.mitayo.data.mal.MalHttpException
import it.emanuelemelini.mitayo.data.mal.NotLoggedInException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

enum class LoginOutcome { SUCCESS, CANCELLED, FAILED }

/**
 * MAL OAuth2 (authorization code + PKCE "plain"). The client is of type "android", so there's
 * no client secret: the client ID alone identifies the app.
 */
class MalAuth(private val http: OkHttpClient, private val tokenStore: TokenStore) {

    val isLoggedIn: Flow<Boolean> = tokenStore.tokens.map { it != null }.distinctUntilChanged()

    val hasClientId: Boolean get() = BuildConfig.MAL_CLIENT_ID.isNotEmpty()

    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()

    /** Starts a login: the page to open in a Custom Tab. */
    suspend fun authorizeUri(): Uri {
        val login = PendingLogin(verifier = Pkce.newVerifier(), state = Pkce.newState())
        tokenStore.savePendingLogin(login)
        return AUTHORIZE_URL.toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.MAL_CLIENT_ID)
            .appendQueryParameter("state", login.state)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", login.verifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .build()
    }

    /** Completes the login from the redirect mitayo://oauth?code=…&state=…; network errors are thrown. */
    suspend fun completeLogin(redirect: Uri): LoginOutcome {
        val pending = tokenStore.pendingLogin() ?: return LoginOutcome.FAILED
        val error = redirect.getQueryParameter("error")
        if (error != null) {
            tokenStore.clearPendingLogin()
            return if (error == "access_denied") LoginOutcome.CANCELLED else LoginOutcome.FAILED
        }
        val code = redirect.getQueryParameter("code")
        // A redirect that isn't the answer to our own request is ignored
        if (code == null || redirect.getQueryParameter("state") != pending.state) return LoginOutcome.FAILED
        val form = FormBody.Builder()
            .add("client_id", BuildConfig.MAL_CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", pending.verifier)
            .add("redirect_uri", REDIRECT_URI)
            .build()
        val tokens = try {
            requestTokens(form)
        } catch (_: MalHttpException) {
            tokenStore.clearPendingLogin()
            return LoginOutcome.FAILED
        }
        tokenStore.save(tokens)
        tokenStore.clearPendingLogin()
        return LoginOutcome.SUCCESS
    }

    /**
     * A valid access token, refreshed when it's about to expire. [rejected]: a token MAL just
     * refused (401), which forces a refresh unless another request already got a new one.
     * Throws [NotLoggedInException] when the session is over, IOException when offline.
     */
    suspend fun accessToken(rejected: String? = null): String = refreshMutex.withLock {
        val tokens = tokenStore.current() ?: throw NotLoggedInException()
        val stillValid = tokens.expiresAtMillis - EXPIRY_MARGIN_MS > System.currentTimeMillis()
        // A different token than the rejected one was refreshed meanwhile by another request
        if (stillValid && tokens.accessToken != rejected) return tokens.accessToken
        refresh(tokens)
    }

    suspend fun logout() {
        tokenStore.clear()
    }

    private suspend fun refresh(tokens: MalTokens): String {
        val form = FormBody.Builder()
            .add("client_id", BuildConfig.MAL_CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", tokens.refreshToken)
            .build()
        val fresh = try {
            requestTokens(form)
        } catch (e: MalHttpException) {
            // Refresh token expired or revoked (it lasts about a month): the session is over
            if (e.code in 400..401) {
                tokenStore.clear()
                throw NotLoggedInException()
            }
            throw e
        }
        tokenStore.save(fresh)
        return fresh.accessToken
    }

    private suspend fun requestTokens(form: FormBody): MalTokens {
        val request = Request.Builder().url(TOKEN_URL).post(form).build()
        val (code, body) = http.newCall(request).awaitText()
        if (code !in 200..299) throw MalHttpException(code, body)
        val response = json.decodeFromString(TokenResponse.serializer(), body)
        return MalTokens(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtMillis = System.currentTimeMillis() + response.expiresIn * 1000L,
        )
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("expires_in") val expiresIn: Long,
    )

    companion object {
        /** Must match exactly the App Redirect URL registered on myanimelist.net/apiconfig. */
        const val REDIRECT_URI = "mitayo://oauth"
        private const val AUTHORIZE_URL = "https://myanimelist.net/v1/oauth2/authorize"
        private const val TOKEN_URL = "https://myanimelist.net/v1/oauth2/token"
        private const val EXPIRY_MARGIN_MS = 5 * 60 * 1000L
    }
}
