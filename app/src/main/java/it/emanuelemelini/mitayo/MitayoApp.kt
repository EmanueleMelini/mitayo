package it.emanuelemelini.mitayo

import android.app.Application
import android.content.Context
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import it.emanuelemelini.mitayo.data.CatalogRepository
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.OtherLists
import it.emanuelemelini.mitayo.data.SkippedBackup
import it.emanuelemelini.mitayo.data.StatusEditor
import it.emanuelemelini.mitayo.data.Outbox
import it.emanuelemelini.mitayo.data.SwipeHistory
import it.emanuelemelini.mitayo.data.SyncManager
import it.emanuelemelini.mitayo.data.auth.MalAuth
import it.emanuelemelini.mitayo.data.auth.Session
import it.emanuelemelini.mitayo.data.auth.TokenStore
import it.emanuelemelini.mitayo.data.db.AppDatabase
import it.emanuelemelini.mitayo.data.mal.MalApi
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.push.NewsTopics
import it.emanuelemelini.mitayo.data.reminders.ReminderNotifier
import it.emanuelemelini.mitayo.data.reminders.ReminderScheduler
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.data.update.AppUpdater
import it.emanuelemelini.mitayo.data.update.Changelog
import it.emanuelemelini.mitayo.data.update.GitHubReleasesClient
import it.emanuelemelini.mitayo.data.update.UpdateInstaller
import it.emanuelemelini.mitayo.data.update.WhatsNew
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MitayoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Changes saved offline (or before the app was closed) leave at the first chance
        container.outbox.schedule()
        container.reminderNotifier.createChannels()
        container.applicationScope.launch { container.reminderScheduler.rescheduleAll() }
        val settings = container.settingsRepository.settings
        // Statistics and crash reports follow the user's choices, from the first settings on
        container.applicationScope.launch {
            settings.distinctUntilChanged().collect { container.telemetry.update(it) }
        }
        container.applicationScope.launch {
            settings.map { it.newsNotifications to it.privacyConsentPending }.distinctUntilChanged().collect {
                container.newsTopics.sync()
            }
        }
    }
}

/** Manual DI: the app's shared dependencies, created once. */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val database = AppDatabase.build(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val settingsRepository = SettingsRepository(context)
    val telemetry = Telemetry(context)
    val newsTopics = NewsTopics(context, settingsRepository)
    private val auth = MalAuth(httpClient, TokenStore(context))
    val api = MalApi(httpClient, auth)
    val outbox = Outbox(context, database.outboxDao())
    val listRepository = ListRepository(database, api, outbox)
    val catalogRepository = CatalogRepository(database, api, settingsRepository) { pictures ->
        // Posters of anime removed from the catalog leave the image cache too
        val imageLoader = SingletonImageLoader.get(context)
        pictures.forEach { url ->
            imageLoader.diskCache?.remove(url)
            imageLoader.memoryCache?.remove(MemoryCache.Key(url))
        }
    }
    val deckRepository = DeckRepository(database)
    val statusEditor = StatusEditor(deckRepository, listRepository)
    val swipeHistory = SwipeHistory(database, listRepository, deckRepository)
    val skippedBackup = SkippedBackup(context, database.skippedDao())
    val otherLists = OtherLists(api)
    val syncManager = SyncManager(api, listRepository, catalogRepository, settingsRepository, applicationScope)
    val session = Session(auth, database, outbox, syncManager, settingsRepository, telemetry, applicationScope)
    private val releasesClient = GitHubReleasesClient(httpClient)
    val appUpdater = AppUpdater(
        releasesClient,
        UpdateInstaller(context, httpClient),
        settingsRepository,
        applicationScope,
    )
    val changelog = Changelog(context)
    val whatsNew = WhatsNew(context, settingsRepository, changelog)
    val reminderScheduler = ReminderScheduler(context, settingsRepository)
    val reminderNotifier = ReminderNotifier(
        context,
        settingsRepository,
        session.isLoggedIn,
        deckRepository,
        catalogRepository,
        releasesClient,
    )
}
