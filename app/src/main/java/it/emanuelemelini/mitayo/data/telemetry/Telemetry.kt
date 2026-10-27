package it.emanuelemelini.mitayo.data.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.google.firebase.crashlytics.FirebaseCrashlytics
import it.emanuelemelini.mitayo.AppLocale
import it.emanuelemelini.mitayo.BuildConfig
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.prefs.Settings

/** What a swipe (or the episodes dialog after it, or the ⋯ menu) did: on MAL, or skipped or passed in the app. */
enum class SwipeOutcome { COMPLETED, WATCHING, SKIPPED, PASSED, ON_HOLD, DROPPED, PLAN_TO_WATCH, EPISODE }

/** "What do I watch tonight?" on the categories screen. */
enum class TonightAction { PICK, START }

/** What was shared. */
enum class ShareKind { ANIME, COMPARISON }

/** What the user did on the skipped anime page. */
enum class SkippedAction { RESTORE, SET_STATUS, RESTORE_ALL }

/**
 * Usage statistics (Google Analytics for Firebase) and crash reports (Crashlytics), both off
 * until the user agrees (the manifest disables them at install). Only fixed names of screens,
 * decks and actions are sent: never titles, anime or genre ids, the MAL user name or anything
 * else that comes from MyAnimeList. Debug builds never send anything, and without Firebase
 * ([FirebaseSupport]) every call does nothing.
 */
class Telemetry(private val context: Context) {
    private val available = FirebaseSupport.available(context)
    private val analytics = if (available) FirebaseAnalytics.getInstance(context) else null
    private val crashlytics = if (available) FirebaseCrashlytics.getInstance() else null

    /** Choices last applied: [update] applies them again only when they change. */
    private var applied: Pair<Boolean, Boolean>? = null

    /**
     * Called with every new [Settings] (the first one at start), always in order: the choices
     * are applied before the properties, which are dropped while statistics are off.
     */
    fun update(settings: Settings) {
        if (!available) return
        val choices = (settings.usageStats ?: false) to (settings.crashReports ?: false)
        if (choices != applied) {
            apply(choices.first, choices.second)
            applied = choices
        }
        setUserProperties(settings)
    }

    private fun apply(usageStats: Boolean, crashReports: Boolean) {
        val analytics = analytics ?: return
        val crashlytics = crashlytics ?: return
        val stats = usageStats && !BuildConfig.DEBUG
        // Consent mode: statistics only, never advertising
        analytics.setConsent(
            mapOf(
                ConsentType.ANALYTICS_STORAGE to if (stats) ConsentStatus.GRANTED else ConsentStatus.DENIED,
                ConsentType.AD_STORAGE to ConsentStatus.DENIED,
                ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
                ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
            )
        )
        analytics.setAnalyticsCollectionEnabled(stats)
        // A new random id if the user agrees again later: the old data can't be linked to it
        if (!stats) analytics.resetAnalyticsData()

        val crashes = crashReports && !BuildConfig.DEBUG
        crashlytics.isCrashlyticsCollectionEnabled = crashes
        // Crashes are kept on the phone while collection is off: they must never be sent
        if (!crashes) crashlytics.deleteUnsentReports()
    }

    /** Choices worth comparing in the statistics, no personal values (and not the adult content switch). */
    private fun setUserProperties(settings: Settings) {
        val analytics = analytics ?: return
        analytics.setUserProperty(PROPERTY_THEME, settings.themeMode.name.lowercase())
        analytics.setUserProperty(PROPERTY_TITLE, settings.titles.title.name.lowercase())
        analytics.setUserProperty(PROPERTY_SUBTITLE, settings.titles.subtitle?.name?.lowercase() ?: "none")
        analytics.setUserProperty(PROPERTY_REMINDERS, settings.reminders.values.count { it.enabled }.toString())
        analytics.setUserProperty(PROPERTY_NEWS, settings.newsNotifications.toString())
        setLanguage()
    }

    /** Also from MainActivity: a language change recreates it without changing the settings. */
    fun setLanguage() {
        analytics?.setUserProperty(PROPERTY_LANGUAGE, AppLocale.localizedContext(context).getString(R.string.content_language))
    }

    /** [name]: fixed screen name, never route arguments. */
    fun logScreen(name: String) {
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
            },
        )
    }

    fun logSwipe(category: Category, outcome: SwipeOutcome) {
        analytics?.logEvent(
            EVENT_SWIPE,
            Bundle().apply {
                putString(PARAM_DECK, deckName(category))
                putString(PARAM_OUTCOME, outcome.name.lowercase())
            },
        )
    }

    /** A completed anime with a score; the score itself isn't sent. */
    fun logScore(category: Category) {
        analytics?.logEvent(EVENT_SCORE, Bundle().apply { putString(PARAM_DECK, deckName(category)) })
    }

    fun logUndo(category: Category) {
        analytics?.logEvent(EVENT_UNDO, Bundle().apply { putString(PARAM_DECK, deckName(category)) })
    }

    /** The decks shuffled or back in their order. */
    fun logShuffle(on: Boolean) {
        analytics?.logEvent(EVENT_SHUFFLE, Bundle().apply { putString(PARAM_ON, on.toString()) })
    }

    /** Filters used in a deck; never which ones or their values. */
    fun logDeckFilters(category: Category) {
        analytics?.logEvent(EVENT_FILTERS, Bundle().apply { putString(PARAM_DECK, deckName(category)) })
    }

    /** "What do I watch tonight?": another anime proposed, or the proposed one started. */
    fun logTonight(action: TonightAction) {
        analytics?.logEvent(EVENT_TONIGHT, Bundle().apply { putString(PARAM_ACTION, action.name.lowercase()) })
    }

    /** A swipe undone from the history page. */
    fun logHistoryUndo() {
        analytics?.logEvent(EVENT_HISTORY_UNDO, null)
    }

    /** Something shared from the app: an anime's MAL link or a comparison; never which. */
    fun logShare(what: ShareKind) {
        analytics?.logEvent(EVENT_SHARE, Bundle().apply { putString(PARAM_WHAT, what.name.lowercase()) })
    }

    fun logSkippedAction(action: SkippedAction) {
        analytics?.logEvent(EVENT_SKIPPED_ACTION, Bundle().apply { putString(PARAM_ACTION, action.name.lowercase()) })
    }

    /** A status set from the search; the text searched is never sent. */
    fun logSearchSetStatus() {
        analytics?.logEvent(EVENT_SEARCH_SET_STATUS, null)
    }

    /** A status set on a related anime (sequel, prequel...) from the details. */
    fun logRelatedSetStatus() {
        analytics?.logEvent(EVENT_RELATED_SET_STATUS, null)
    }

    /** Two lists compared; never the user names. */
    fun logCompare(withMe: Boolean) {
        analytics?.logEvent(EVENT_COMPARE, Bundle().apply { putString(PARAM_WITH_ME, withMe.toString()) })
    }

    /** A status set from a comparison, on an anime of the other list. */
    fun logCompareSetStatus() {
        analytics?.logEvent(EVENT_COMPARE_SET_STATUS, null)
    }

    /** A search sent to MyAnimeList; the text searched is never sent here. */
    fun logMalSearch() {
        analytics?.logEvent(EVENT_MAL_SEARCH, null)
    }

    fun logLogin() {
        analytics?.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply { putString(FirebaseAnalytics.Param.METHOD, "myanimelist") })
    }

    /** The special lists by name; every genre deck as "genre" (which genre is MAL data), decade and user decks alike. */
    private fun deckName(category: Category): String = when (category) {
        is Category.OfGenre -> "genre"
        is Category.OfDecade -> "decade"
        is Category.OfUser -> "user"
        is Category.OfStatus -> "status_${category.status.apiValue}"
        is Category.OfSeason -> "season_other"
        else -> category.key
    }

    private companion object {
        const val EVENT_SWIPE = "swipe"
        const val EVENT_UNDO = "swipe_undo"
        const val EVENT_SCORE = "score"
        const val EVENT_SKIPPED_ACTION = "skipped_action"
        const val EVENT_SEARCH_SET_STATUS = "search_set_status"
        const val EVENT_MAL_SEARCH = "search_mal"
        const val EVENT_RELATED_SET_STATUS = "related_set_status"
        const val EVENT_COMPARE = "compare"
        const val EVENT_COMPARE_SET_STATUS = "compare_set_status"
        const val EVENT_SHUFFLE = "deck_shuffle"
        const val EVENT_TONIGHT = "tonight"
        const val EVENT_HISTORY_UNDO = "history_undo"
        const val EVENT_SHARE = "share"
        const val PARAM_WHAT = "what"
        const val EVENT_FILTERS = "deck_filters"
        const val PARAM_ON = "on"
        const val PARAM_WITH_ME = "with_me"
        const val PARAM_DECK = "deck"
        const val PARAM_OUTCOME = "outcome"
        const val PARAM_ACTION = "action"
        const val PROPERTY_THEME = "theme"
        const val PROPERTY_TITLE = "title_language"
        const val PROPERTY_SUBTITLE = "subtitle_language"
        const val PROPERTY_REMINDERS = "reminders_on"
        const val PROPERTY_NEWS = "news"
        const val PROPERTY_LANGUAGE = "app_language"
    }
}
