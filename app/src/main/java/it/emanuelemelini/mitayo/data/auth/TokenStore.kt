package it.emanuelemelini.mitayo.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class MalTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
)

/** Login started in the browser, waiting for the redirect (survives the app being killed meanwhile). */
data class PendingLogin(val verifier: String, val state: String)

private val Context.authDataStore by preferencesDataStore(name = "auth")

/** MAL session: tokens encrypted with [KeystoreCipher], kept apart from the settings. */
class TokenStore(context: Context) {
    private val dataStore = context.applicationContext.authDataStore

    /** null when logged out, or when the tokens can't be decrypted any more (e.g. new Keystore key). */
    val tokens: Flow<MalTokens?> = dataStore.data.map { prefs -> prefs[KEY_TOKENS]?.let(::decode) }

    suspend fun current(): MalTokens? = tokens.first()

    suspend fun save(tokens: MalTokens) {
        val encoded = KeystoreCipher.encrypt(Json.encodeToString(MalTokens.serializer(), tokens))
        dataStore.edit { it[KEY_TOKENS] = encoded }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(KEY_TOKENS) }
    }

    suspend fun savePendingLogin(login: PendingLogin) {
        dataStore.edit {
            it[KEY_VERIFIER] = login.verifier
            it[KEY_STATE] = login.state
        }
    }

    suspend fun pendingLogin(): PendingLogin? {
        val prefs = dataStore.data.first()
        val verifier = prefs[KEY_VERIFIER] ?: return null
        val state = prefs[KEY_STATE] ?: return null
        return PendingLogin(verifier, state)
    }

    suspend fun clearPendingLogin() {
        dataStore.edit {
            it.remove(KEY_VERIFIER)
            it.remove(KEY_STATE)
        }
    }

    private fun decode(encoded: String): MalTokens? = runCatching {
        Json.decodeFromString(MalTokens.serializer(), KeystoreCipher.decrypt(encoded))
    }.getOrNull()

    private companion object {
        val KEY_TOKENS = stringPreferencesKey("tokens")
        val KEY_VERIFIER = stringPreferencesKey("pending_verifier")
        val KEY_STATE = stringPreferencesKey("pending_state")
    }
}
