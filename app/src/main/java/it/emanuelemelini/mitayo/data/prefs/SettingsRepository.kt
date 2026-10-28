package it.emanuelemelini.mitayo.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import it.emanuelemelini.mitayo.data.AffinityMethod
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.TitleLanguage
import it.emanuelemelini.mitayo.data.reminders.ReminderConfig
import it.emanuelemelini.mitayo.data.reminders.ReminderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalTime

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** The user's choices, plus the MAL profile shown in the app. */
data class Settings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val titles: TitleChoice = TitleChoice(),
    val showNsfw: Boolean = false,
    val userName: String? = null,
    val userPicture: String? = null,
    /** Version of terms of use and privacy policy accepted before the login (0 = never). */
    val policiesAcceptedVersion: Int = 0,
    /** Usage statistics and crash reports (Firebase): null until the question after the login is answered. */
    val usageStats: Boolean? = null,
    val crashReports: Boolean? = null,
    /** Mitayo news (push): on unless turned off, but nothing is registered before the privacy question. */
    val newsNotifications: Boolean = true,
    val reminders: Map<ReminderType, ReminderConfig> = ReminderType.entries.associateWith { ReminderConfig(it) },
    /** Every deck in a random order (a new one at every visit) instead of its own. */
    val shuffleDecks: Boolean = false,
    /** The swipe guide was shown, at the first deck opened. */
    val deckGuideSeen: Boolean = false,
    /** How a comparison works out the affinity: both stay available. */
    val affinityMethod: AffinityMethod = AffinityMethod.SHARED_SCORES,
) {
    val privacyConsentPending: Boolean get() = usageStats == null || crashReports == null
}

/** How far the catalog and the list are synced (times in epoch millis, 0 = never). */
data class SyncInfo(
    /** Offset of the next popularity page to download. */
    val catalogNextOffset: Int = 0,
    /** MAL has no more pages. */
    val catalogComplete: Boolean = false,
    val catalogSyncedAt: Long = 0,
    val listSyncedAt: Long = 0,
)

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            themeMode = prefs[KEY_THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.DARK,
            titles = TitleChoice(
                // Before 1.0.0 there was only the "English titles" switch
                title = prefs[KEY_TITLE_LANGUAGE]?.let(::titleLanguage)
                    ?: if (prefs[KEY_ENGLISH_TITLES] == true) TitleLanguage.ENGLISH else TitleLanguage.ROMAJI,
                subtitle = prefs[KEY_SUBTITLE_LANGUAGE].let { if (it == null) TitleLanguage.JAPANESE else titleLanguage(it) },
            ),
            showNsfw = prefs[KEY_SHOW_NSFW] ?: false,
            userName = prefs[KEY_USER_NAME],
            userPicture = prefs[KEY_USER_PICTURE],
            policiesAcceptedVersion = prefs[KEY_POLICIES_ACCEPTED] ?: 0,
            usageStats = prefs[KEY_USAGE_STATS],
            crashReports = prefs[KEY_CRASH_REPORTS],
            newsNotifications = prefs[KEY_NEWS] ?: true,
            reminders = ReminderType.entries.associateWith { type ->
                ReminderConfig(
                    type = type,
                    enabled = prefs[reminderEnabledKey(type)] ?: false,
                    time = prefs[reminderTimeKey(type)]?.let { LocalTime.ofSecondOfDay(it * 60L) } ?: type.defaultTime,
                )
            },
            shuffleDecks = prefs[KEY_SHUFFLE_DECKS] ?: false,
            deckGuideSeen = prefs[KEY_DECK_GUIDE_SEEN] ?: false,
            affinityMethod = prefs[KEY_AFFINITY_METHOD]?.let { name -> AffinityMethod.entries.firstOrNull { it.name == name } }
                ?: AffinityMethod.SHARED_SCORES,
        )
    }

    val syncInfo: Flow<SyncInfo> = dataStore.data.map { prefs ->
        SyncInfo(
            catalogNextOffset = prefs[KEY_CATALOG_NEXT_OFFSET] ?: 0,
            catalogComplete = prefs[KEY_CATALOG_COMPLETE] ?: false,
            catalogSyncedAt = prefs[KEY_CATALOG_SYNCED_AT] ?: 0L,
            listSyncedAt = prefs[KEY_LIST_SYNCED_AT] ?: 0L,
        )
    }

    /** MAL user names used for comparisons and other users' decks, the last one first. */
    val recentUsers: Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[KEY_RECENT_USERS]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun addRecentUser(name: String) = edit { prefs ->
        val current = prefs[KEY_RECENT_USERS]?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        prefs[KEY_RECENT_USERS] = (listOf(name) + current.filterNot { it.equals(name, ignoreCase = true) })
            .take(MAX_RECENT_USERS)
            .joinToString(",")
    }

    suspend fun clearRecentUsers() = edit { it.remove(KEY_RECENT_USERS) }

    suspend fun currentSettings(): Settings = settings.first()

    suspend fun currentSyncInfo(): SyncInfo = syncInfo.first()

    suspend fun setThemeMode(mode: ThemeMode) = edit { it[KEY_THEME] = mode.name }

    suspend fun setTitleLanguage(language: TitleLanguage) = edit { it[KEY_TITLE_LANGUAGE] = language.name }

    /** null: no subtitle. */
    suspend fun setSubtitleLanguage(language: TitleLanguage?) = edit { it[KEY_SUBTITLE_LANGUAGE] = language?.name ?: NO_SUBTITLE }

    suspend fun setShowNsfw(enabled: Boolean) = edit { it[KEY_SHOW_NSFW] = enabled }

    suspend fun setShuffleDecks(enabled: Boolean) = edit { it[KEY_SHUFFLE_DECKS] = enabled }

    suspend fun setDeckGuideSeen() = edit { it[KEY_DECK_GUIDE_SEEN] = true }

    suspend fun setAffinityMethod(method: AffinityMethod) = edit { it[KEY_AFFINITY_METHOD] = method.name }

    suspend fun setPoliciesAccepted(version: Int) = edit { it[KEY_POLICIES_ACCEPTED] = version }

    /** The question after the login: one answer for both. */
    suspend fun setPrivacyConsent(granted: Boolean) = edit {
        it[KEY_USAGE_STATS] = granted
        it[KEY_CRASH_REPORTS] = granted
    }

    suspend fun setUsageStats(enabled: Boolean) = edit { it[KEY_USAGE_STATS] = enabled }

    suspend fun setCrashReports(enabled: Boolean) = edit { it[KEY_CRASH_REPORTS] = enabled }

    suspend fun setNewsNotifications(enabled: Boolean) = edit { it[KEY_NEWS] = enabled }

    suspend fun setReminderEnabled(type: ReminderType, enabled: Boolean) = edit { it[reminderEnabledKey(type)] = enabled }

    suspend fun setReminderTime(type: ReminderType, time: LocalTime) = edit {
        it[reminderTimeKey(type)] = time.hour * 60 + time.minute
    }

    /** Topics subscribed with a messaging token: turning the news off must delete it. */
    suspend fun newsSubscribed(): Boolean = dataStore.data.first()[KEY_NEWS_SUBSCRIBED] ?: false

    suspend fun setNewsSubscribed(subscribed: Boolean) = edit { it[KEY_NEWS_SUBSCRIBED] = subscribed }

    /** Last version announced by the update reminder. */
    suspend fun lastNotifiedUpdate(): String? = dataStore.data.first()[KEY_LAST_NOTIFIED_UPDATE]

    suspend fun setLastNotifiedUpdate(version: String) = edit { it[KEY_LAST_NOTIFIED_UPDATE] = version }

    /** Last season announced ("2026-fall"). */
    suspend fun lastNotifiedSeason(): String? = dataStore.data.first()[KEY_LAST_NOTIFIED_SEASON]

    suspend fun setLastNotifiedSeason(key: String) = edit { it[KEY_LAST_NOTIFIED_SEASON] = key }

    suspend fun setUser(name: String, picture: String?) = edit {
        it[KEY_USER_NAME] = name
        if (picture != null) it[KEY_USER_PICTURE] = picture else it.remove(KEY_USER_PICTURE)
    }

    suspend fun setCatalogProgress(nextOffset: Int, complete: Boolean, syncedAt: Long) = edit {
        it[KEY_CATALOG_NEXT_OFFSET] = nextOffset
        it[KEY_CATALOG_COMPLETE] = complete
        it[KEY_CATALOG_SYNCED_AT] = syncedAt
    }

    suspend fun setListSyncedAt(time: Long) = edit { it[KEY_LIST_SYNCED_AT] = time }

    /** Epoch day of the last update check on GitHub (at most one automatic check a day). */
    suspend fun lastUpdateCheckDay(): Long? = dataStore.data.first()[KEY_LAST_UPDATE_CHECK]

    suspend fun setLastUpdateCheckDay(epochDay: Long) = edit { it[KEY_LAST_UPDATE_CHECK] = epochDay }

    /** versionName whose "What's new" was already shown (null: installed before 1.0.0, or never started). */
    suspend fun lastSeenVersion(): String? = dataStore.data.first()[KEY_LAST_SEEN_VERSION]

    suspend fun setLastSeenVersion(versionName: String) = edit { it[KEY_LAST_SEEN_VERSION] = versionName }

    suspend fun remoteListRefreshedAt(listKey: String): Long = dataStore.data.first()[remoteKey(listKey)] ?: 0L

    suspend fun setRemoteListRefreshedAt(listKey: String, time: Long) = edit { it[remoteKey(listKey)] = time }

    /** Logout: the profile and the user's own lists go, the catalog (the same for everyone) stays. */
    suspend fun clearUser(remoteListKeys: List<String>) = edit { prefs ->
        prefs.remove(KEY_USER_NAME)
        prefs.remove(KEY_USER_PICTURE)
        prefs.remove(KEY_LIST_SYNCED_AT)
        prefs.remove(KEY_RECENT_USERS)
        remoteListKeys.forEach { prefs.remove(remoteKey(it)) }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    /** null for [NO_SUBTITLE] or an unknown value. */
    private fun titleLanguage(name: String): TitleLanguage? = TitleLanguage.entries.firstOrNull { it.name == name }

    private fun remoteKey(listKey: String): Preferences.Key<Long> = longPreferencesKey("remote_refreshed_$listKey")

    private fun reminderEnabledKey(type: ReminderType) = booleanPreferencesKey("reminder_${type.name.lowercase()}_enabled")

    /** Minutes after midnight. */
    private fun reminderTimeKey(type: ReminderType) = intPreferencesKey("reminder_${type.name.lowercase()}_time")

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_mode")
        /** Read only to keep the choice made before 1.0.0. */
        val KEY_ENGLISH_TITLES = booleanPreferencesKey("english_titles")
        val KEY_TITLE_LANGUAGE = stringPreferencesKey("title_language")
        val KEY_SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
        const val NO_SUBTITLE = "NONE"
        val KEY_SHOW_NSFW = booleanPreferencesKey("show_nsfw")
        val KEY_SHUFFLE_DECKS = booleanPreferencesKey("shuffle_decks")
        val KEY_DECK_GUIDE_SEEN = booleanPreferencesKey("deck_guide_seen")
        val KEY_AFFINITY_METHOD = stringPreferencesKey("affinity_method")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_PICTURE = stringPreferencesKey("user_picture")
        val KEY_POLICIES_ACCEPTED = intPreferencesKey("policies_accepted_version")
        val KEY_CATALOG_NEXT_OFFSET = intPreferencesKey("catalog_next_offset")
        val KEY_CATALOG_COMPLETE = booleanPreferencesKey("catalog_complete")
        val KEY_CATALOG_SYNCED_AT = longPreferencesKey("catalog_synced_at")
        val KEY_LIST_SYNCED_AT = longPreferencesKey("list_synced_at")
        val KEY_LAST_UPDATE_CHECK = longPreferencesKey("last_update_check_day")
        val KEY_LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
        val KEY_USAGE_STATS = booleanPreferencesKey("usage_stats")
        val KEY_CRASH_REPORTS = booleanPreferencesKey("crash_reports")
        val KEY_NEWS = booleanPreferencesKey("news_notifications")
        val KEY_NEWS_SUBSCRIBED = booleanPreferencesKey("news_subscribed")
        val KEY_LAST_NOTIFIED_UPDATE = stringPreferencesKey("last_notified_update")
        val KEY_LAST_NOTIFIED_SEASON = stringPreferencesKey("last_notified_season")
        val KEY_RECENT_USERS = stringPreferencesKey("recent_users")
        const val MAX_RECENT_USERS = 6
    }
}
