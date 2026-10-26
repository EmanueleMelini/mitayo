package it.emanuelemelini.mitayo.data.push

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import it.emanuelemelini.mitayo.AppLocale
import it.emanuelemelini.mitayo.BuildConfig
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.reminders.ReminderNotifier
import it.emanuelemelini.mitayo.data.telemetry.FirebaseSupport
import it.emanuelemelini.mitayo.ui.LaunchRequest
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Mitayo news (Firebase Cloud Messaging topics): [ALL] reaches every install, "news-it" /
 * "news-en" who uses the app in that language, [DEBUG] only debug builds (to try a message
 * first). Sent from the Firebase console and, for new versions, by the release workflow. No
 * server and no account: the topic is all Firebase knows. Before the privacy question is
 * answered, or with the news off, the phone has no messaging token at all.
 */
class NewsTopics(private val context: Context, private val settingsRepository: SettingsRepository) {

    /** At every start (the language may have changed) and when the choices change. */
    suspend fun sync() {
        if (!FirebaseSupport.available(context)) return
        val settings = settingsRepository.currentSettings()
        val messaging = FirebaseMessaging.getInstance()
        if (settings.newsNotifications && !settings.privacyConsentPending) {
            messaging.isAutoInitEnabled = true
            val wanted = buildSet {
                add(ALL)
                add(languageTopic(AppLocale.localizedContext(context).getString(R.string.content_language)))
                if (BuildConfig.DEBUG) add(DEBUG)
            }
            // Subscribing again is harmless; Firebase retries by itself when offline
            (LANGUAGES.map(::languageTopic) + ALL + DEBUG).forEach { topic ->
                val task = if (topic in wanted) messaging.subscribeToTopic(topic) else messaging.unsubscribeFromTopic(topic)
                // e.g. no Google Play services on the phone: there are no push notifications at all
                task.addOnFailureListener { Log.i(TAG, "Topic $topic not updated: ${it.message}") }
            }
            settingsRepository.setNewsSubscribed(true)
        } else {
            messaging.isAutoInitEnabled = false
            // Without the token no topic reaches the phone any more; offline it's tried at the next start
            if (settingsRepository.newsSubscribed() && messaging.deleteTopicToken().succeeded()) {
                settingsRepository.setNewsSubscribed(false)
            }
        }
    }

    /**
     * Its replacement, unregister(), works only in the opt-in "installation id" mode
     * (firebase_messaging_installation_id_enabled), where tokens throw; topics still use the token.
     */
    @Suppress("DEPRECATION")
    private fun FirebaseMessaging.deleteTopicToken(): Task<Void> = deleteToken()

    private suspend fun Task<*>.succeeded(): Boolean = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { continuation.resume(it.isSuccessful) }
    }

    companion object {
        const val ALL = "news"
        const val DEBUG = "news-debug"
        /** The app languages (R.string.content_language). */
        private val LANGUAGES = listOf("it", "en")
        private const val TAG = "NewsTopics"

        fun languageTopic(language: String) = "news-$language"
    }
}

/**
 * With the app in the background the system shows the notification by itself (default channel
 * and icon from the manifest, data as extras of the launch intent); with the app in the
 * foreground it comes here and is shown the same way, on the same channel and with the same tap.
 */
class NewsMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val title = notification.title ?: getString(R.string.app_name)
        val text = notification.body.orEmpty()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val channel = notification.channelId?.takeIf { it in CHANNELS } ?: CHANNEL
        val open = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            LaunchRequest.intent(this, LaunchRequest.from(message.data) ?: LaunchRequest.OpenCategories),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(this, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Topics don't need the token: nothing to send anywhere. Kept (although deprecated in the
     * SDK) because lint asks every messaging service to handle token refreshes.
     */
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) = Unit

    companion object {
        /** Also the default channel of the manifest, for the notifications shown by the system. */
        const val CHANNEL = "news"

        /** Channels a message may ask for: the version push goes to the updates one. */
        private val CHANNELS = setOf(CHANNEL, ReminderNotifier.CHANNEL_UPDATES)

        /** A new message replaces the previous one still shown. */
        private const val NOTIFICATION_ID = 2000
    }
}
