package it.emanuelemelini.mitayo.data.anilist

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.emanuelemelini.mitayo.data.auth.KeystoreCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The linked AniList account. */
data class AniListAccount(
    val userId: Int,
    val name: String,
    val avatar: String?,
    val scoreFormat: ScoreFormat,
    /** AniList's 18+ content; off, AniList hides the adult anime from Mitayo too. */
    val adultContent: Boolean,
)

/** The AniList link as the Profile shows it. */
data class AniListInfo(
    /** null: not linked. */
    val account: AniListAccount? = null,
    /** The last link ended because the token expired (it lasts a year). */
    val expired: Boolean = false,
    val syncedAt: Long = 0,
    /** Anime of the AniList list that MAL hasn't. */
    val notOnMal: Int = 0,
)

private val Context.aniListDataStore by preferencesDataStore(name = "anilist")

/** The AniList link: the token encrypted with [KeystoreCipher], the account and the numbers of the last sync. */
class AniListStore(context: Context) {
    private val dataStore = context.applicationContext.aniListDataStore

    val info: Flow<AniListInfo> = dataStore.data.map { prefs ->
        AniListInfo(
            account = prefs.account(),
            expired = prefs[KEY_EXPIRED] == 1,
            syncedAt = prefs[KEY_SYNCED_AT] ?: 0,
            notOnMal = prefs[KEY_NOT_ON_MAL] ?: 0,
        )
    }

    suspend fun currentInfo(): AniListInfo = info.first()

    /** null when not linked, expired, or when the token can't be decrypted any more (new Keystore key). */
    suspend fun token(): String? {
        val prefs = dataStore.data.first()
        if ((prefs[KEY_EXPIRES_AT] ?: 0) < System.currentTimeMillis()) return null
        return prefs[KEY_TOKEN]?.let { runCatching { KeystoreCipher.decrypt(it) }.getOrNull() }
    }

    suspend fun hasToken(): Boolean = dataStore.data.first()[KEY_TOKEN] != null

    suspend fun link(token: String, expiresAt: Long, user: AniListUser) {
        val encoded = KeystoreCipher.encrypt(token)
        dataStore.edit {
            it.clear()
            it[KEY_TOKEN] = encoded
            it[KEY_EXPIRES_AT] = expiresAt
            it.putUser(user)
        }
    }

    suspend fun updateUser(user: AniListUser) {
        dataStore.edit { it.putUser(user) }
    }

    suspend fun setSynced(at: Long, notOnMal: Int) {
        dataStore.edit {
            it[KEY_SYNCED_AT] = at
            it[KEY_NOT_ON_MAL] = notOnMal
        }
    }

    /** Everything goes; [expired] stays to say why. */
    suspend fun unlink(expired: Boolean) {
        dataStore.edit {
            it.clear()
            if (expired) it[KEY_EXPIRED] = 1
        }
    }

    /** A login started in the browser, waiting for the redirect. */
    suspend fun setPendingLogin(at: Long?) {
        dataStore.edit { if (at == null) it.remove(KEY_PENDING_LOGIN) else it[KEY_PENDING_LOGIN] = at }
    }

    suspend fun pendingLogin(): Long? = dataStore.data.first()[KEY_PENDING_LOGIN]

    private fun Preferences.account(): AniListAccount? {
        if (this[KEY_TOKEN] == null) return null
        return AniListAccount(
            userId = this[KEY_USER_ID] ?: return null,
            name = this[KEY_NAME] ?: return null,
            avatar = this[KEY_AVATAR],
            scoreFormat = ScoreFormat.fromApi(this[KEY_SCORE_FORMAT]),
            // Unknown until the first sync that asks for it: no warning in the meantime
            adultContent = this[KEY_ADULT_CONTENT] != 0,
        )
    }

    private fun MutablePreferences.putUser(user: AniListUser) {
        this[KEY_USER_ID] = user.id
        this[KEY_NAME] = user.name
        user.avatar?.medium?.let { this[KEY_AVATAR] = it } ?: remove(KEY_AVATAR)
        this[KEY_SCORE_FORMAT] = ScoreFormat.fromApi(user.mediaListOptions?.scoreFormat).name
        this[KEY_ADULT_CONTENT] = if (user.options?.displayAdultContent == true) 1 else 0
    }

    private companion object {
        val KEY_TOKEN = stringPreferencesKey("token")
        val KEY_EXPIRES_AT = longPreferencesKey("expires_at")
        val KEY_USER_ID = intPreferencesKey("user_id")
        val KEY_NAME = stringPreferencesKey("name")
        val KEY_AVATAR = stringPreferencesKey("avatar")
        val KEY_SCORE_FORMAT = stringPreferencesKey("score_format")
        val KEY_ADULT_CONTENT = intPreferencesKey("adult_content")
        val KEY_SYNCED_AT = longPreferencesKey("synced_at")
        val KEY_NOT_ON_MAL = intPreferencesKey("not_on_mal")
        val KEY_EXPIRED = intPreferencesKey("expired")
        val KEY_PENDING_LOGIN = longPreferencesKey("pending_login")
    }
}
