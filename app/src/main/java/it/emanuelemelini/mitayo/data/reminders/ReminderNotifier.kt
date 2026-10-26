package it.emanuelemelini.mitayo.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.emanuelemelini.mitayo.AppLocale
import it.emanuelemelini.mitayo.BuildConfig
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.CatalogRepository
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.prefs.Settings
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.push.NewsMessagingService
import it.emanuelemelini.mitayo.data.update.AppVersion
import it.emanuelemelini.mitayo.data.update.GitHubReleasesClient
import it.emanuelemelini.mitayo.data.update.UpdateNotice
import it.emanuelemelini.mitayo.ui.LaunchRequest
import it.emanuelemelini.mitayo.ui.components.seasonNameRes
import java.text.NumberFormat
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Builds and shows the local notifications (the news come from Firebase, [NewsMessagingService]). */
class ReminderNotifier(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val isLoggedIn: Flow<Boolean>,
    private val deckRepository: DeckRepository,
    private val catalogRepository: CatalogRepository,
    private val releasesClient: GitHubReleasesClient,
) {
    /** In the app language, also before Android 13. */
    private val res get() = AppLocale.localizedContext(context).resources

    /** Called at every app start, so channel names follow the current language. */
    fun createChannels() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_UPDATES, res.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_SEASON, res.getString(R.string.channel_season), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(CHANNEL_REMINDERS, res.getString(R.string.channel_reminders), NotificationManager.IMPORTANCE_DEFAULT),
                NotificationChannel(NewsMessagingService.CHANNEL, res.getString(R.string.channel_news), NotificationManager.IMPORTANCE_DEFAULT),
            )
        )
    }

    /** Throws when GitHub or MAL don't answer: [ReminderWorker] tries again. */
    suspend fun show(type: ReminderType) {
        val settings = settingsRepository.currentSettings()
        if (settings.reminders[type]?.enabled != true || !notificationsAllowed(context)) return
        val builder = when (type) {
            ReminderType.UPDATE -> updateNotification()
            ReminderType.SEASON -> seasonNotification(settings)
            ReminderType.WEEKLY -> weeklyNotification(settings)
        } ?: return
        notify(type, builder)
    }

    /** null when there is nothing new, or in a debug build (signed with another key, it couldn't install it). */
    private suspend fun updateNotification(): NotificationCompat.Builder? {
        if (BuildConfig.DEBUG) return null
        val release = releasesClient.latestRelease() ?: return null
        val lastNotified = settingsRepository.lastNotifiedUpdate()?.let(AppVersion::parse)
        if (!UpdateNotice.shouldNotify(AppVersion.parse(BuildConfig.VERSION_NAME), release.version, lastNotified)) return null
        settingsRepository.setLastNotifiedUpdate(release.version.toString())
        val text = release.notesFor(res.getString(R.string.content_language)).firstOrNull()
            ?: res.getString(R.string.reminder_update_text)
        return base(CHANNEL_UPDATES, res.getString(R.string.reminder_update_title, release.version.toString()), text)
            .setContentIntent(activity(ReminderType.UPDATE, LaunchRequest.ShowUpdate))
    }

    /** Once per season, in its first days, with the anime of the season still to swipe. */
    private suspend fun seasonNotification(settings: Settings): NotificationCompat.Builder? {
        if (!isLoggedIn.first()) return null
        val season = ReminderSchedule.seasonToAnnounce(LocalDate.now(), settingsRepository.lastNotifiedSeason()) ?: return null
        catalogRepository.refreshRemoteList(Category.Season)
        val remaining = deckRepository.observeRemaining(Category.Season, settings.showNsfw).first()
        settingsRepository.setLastNotifiedSeason(ReminderSchedule.seasonKey(season))
        if (remaining == 0) return null
        val name = res.getString(R.string.season_format, res.getString(seasonNameRes(season.season) ?: return null), season.year)
        return base(
            CHANNEL_SEASON,
            res.getString(R.string.reminder_season_title, name),
            res.getQuantityString(R.plurals.reminder_season_text, remaining, NumberFormat.getIntegerInstance().format(remaining)),
        ).setContentIntent(activity(ReminderType.SEASON, LaunchRequest.OpenDeck(Category.Season.key)))
    }

    /** Skipped when every downloaded popular anime is already swiped. */
    private suspend fun weeklyNotification(settings: Settings): NotificationCompat.Builder? {
        if (!isLoggedIn.first()) return null
        val remaining = deckRepository.observeRemaining(Category.Popular, settings.showNsfw).first()
        if (remaining == 0) return null
        return base(
            CHANNEL_REMINDERS,
            res.getString(R.string.reminder_weekly_title),
            res.getQuantityString(R.plurals.reminder_weekly_text, remaining, NumberFormat.getIntegerInstance().format(remaining)),
        ).setContentIntent(activity(ReminderType.WEEKLY, LaunchRequest.OpenDeck(Category.Popular.key)))
    }

    private fun notify(type: ReminderType, builder: NotificationCompat.Builder) {
        // Checked again right before: lint wants the check next to notify()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_BASE + type.ordinal, builder.build())
    }

    private fun base(channel: String, title: String, text: String) =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)

    private fun activity(type: ReminderType, request: LaunchRequest): PendingIntent = PendingIntent.getActivity(
        context,
        type.ordinal,
        LaunchRequest.intent(context, request),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        /** Also the channel of the version push sent by the release workflow (notify_update.py). */
        const val CHANNEL_UPDATES = "updates"
        const val CHANNEL_SEASON = "season"
        const val CHANNEL_REMINDERS = "reminders"
        private const val NOTIFICATION_ID_BASE = 1000
    }
}
