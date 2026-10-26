package it.emanuelemelini.mitayo.ui

import android.content.Context
import android.content.Intent
import it.emanuelemelini.mitayo.MainActivity

/**
 * Where a notification or a launcher shortcut takes the user. The same keys travel as intent
 * extras (local notifications, res/xml/shortcuts.xml) and as the data of a push, which the
 * system turns into extras when it shows the notification itself.
 */
sealed interface LaunchRequest {
    data object OpenCategories : LaunchRequest

    /** Looks for the new version now (the daily limit doesn't count) and shows its dialog. */
    data object ShowUpdate : LaunchRequest

    /** [categoryKey]: a Category key. */
    data class OpenDeck(val categoryKey: String) : LaunchRequest

    /** The search page, from the launcher shortcut. */
    data object OpenSearch : LaunchRequest

    companion object {
        private const val KEY_ACTION = "launch_action"
        private const val KEY_PAYLOAD = "launch_payload"
        private const val ACTION_CATEGORIES = "categories"
        private const val ACTION_SHOW_UPDATE = "show_update"
        private const val ACTION_DECK = "deck"
        private const val ACTION_SEARCH = "search"

        fun intent(context: Context, request: LaunchRequest): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply {
                    when (request) {
                        OpenCategories -> putExtra(KEY_ACTION, ACTION_CATEGORIES)
                        ShowUpdate -> putExtra(KEY_ACTION, ACTION_SHOW_UPDATE)
                        is OpenDeck -> putExtra(KEY_ACTION, ACTION_DECK).putExtra(KEY_PAYLOAD, request.categoryKey)
                        OpenSearch -> putExtra(KEY_ACTION, ACTION_SEARCH)
                    }
                }

        fun from(intent: Intent?): LaunchRequest? =
            intent?.let { parse(it.getStringExtra(KEY_ACTION), it.getStringExtra(KEY_PAYLOAD)) }

        /** The data of a push received with the app in the foreground. */
        fun from(data: Map<String, String>): LaunchRequest? = parse(data[KEY_ACTION], data[KEY_PAYLOAD])

        private fun parse(action: String?, payload: String?): LaunchRequest? = when (action) {
            ACTION_CATEGORIES -> OpenCategories
            ACTION_SHOW_UPDATE -> ShowUpdate
            ACTION_DECK -> payload?.let(::OpenDeck)
            ACTION_SEARCH -> OpenSearch
            else -> null
        }

        /** Consumed: a recreation or a later start must not repeat it. */
        fun clear(intent: Intent?) {
            intent?.removeExtra(KEY_ACTION)
            intent?.removeExtra(KEY_PAYLOAD)
        }
    }
}
