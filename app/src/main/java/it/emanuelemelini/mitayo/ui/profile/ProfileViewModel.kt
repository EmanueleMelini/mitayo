package it.emanuelemelini.mitayo.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.AffinityMethod
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.InvalidBackupException
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.Outbox
import it.emanuelemelini.mitayo.data.SkippedBackup
import it.emanuelemelini.mitayo.data.SyncManager
import it.emanuelemelini.mitayo.data.SyncState
import it.emanuelemelini.mitayo.data.TitleLanguage
import it.emanuelemelini.mitayo.data.auth.Session
import it.emanuelemelini.mitayo.data.prefs.Settings
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.prefs.SyncInfo
import it.emanuelemelini.mitayo.data.prefs.ThemeMode
import it.emanuelemelini.mitayo.data.reminders.ReminderScheduler
import it.emanuelemelini.mitayo.data.reminders.ReminderType
import it.emanuelemelini.mitayo.ui.UiText
import it.emanuelemelini.mitayo.ui.uiText
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileStats(
    val completed: Int = 0,
    val skipped: Int = 0,
    /** Changes still to send to MAL. */
    val unsent: Int = 0,
)

data class ProfileUiState(
    val settings: Settings = Settings(),
    val stats: ProfileStats = ProfileStats(),
    val syncInfo: SyncInfo = SyncInfo(),
    val sync: SyncState = SyncState.Idle,
)

class ProfileViewModel(
    private val settingsRepository: SettingsRepository,
    listRepository: ListRepository,
    deckRepository: DeckRepository,
    outbox: Outbox,
    private val syncManager: SyncManager,
    private val session: Session,
    private val reminderScheduler: ReminderScheduler,
    private val skippedBackup: SkippedBackup,
) : ViewModel() {

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    /** One-off messages for the snackbar of the Profile pages. */
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private val stats = combine(
        listRepository.completedCount,
        deckRepository.skippedCount,
        outbox.count,
    ) { completed, skipped, unsent -> ProfileStats(completed, skipped, unsent) }

    val uiState: StateFlow<ProfileUiState> = combine(
        settingsRepository.settings,
        stats,
        settingsRepository.syncInfo,
        syncManager.state,
    ) { settings, stats, syncInfo, sync -> ProfileUiState(settings, stats, syncInfo, sync) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setTitleLanguage(language: TitleLanguage) {
        viewModelScope.launch { settingsRepository.setTitleLanguage(language) }
    }

    fun setSubtitleLanguage(language: TitleLanguage?) {
        viewModelScope.launch { settingsRepository.setSubtitleLanguage(language) }
    }

    fun setShowNsfw(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowNsfw(enabled) }
    }

    fun setAffinityMethod(method: AffinityMethod) {
        viewModelScope.launch { settingsRepository.setAffinityMethod(method) }
    }

    fun setNewsNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNewsNotifications(enabled) }
    }

    fun setReminderEnabled(type: ReminderType, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(type, enabled)
            reminderScheduler.reschedule(type)
        }
    }

    fun setReminderTime(type: ReminderType, time: LocalTime) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(type, time)
            reminderScheduler.reschedule(type)
        }
    }

    fun setUsageStats(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setUsageStats(enabled) }
    }

    fun setCrashReports(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setCrashReports(enabled) }
    }

    fun syncNow() = syncManager.syncNow()

    fun exportSkipped(uri: Uri) = backup { skippedBackup.export(uri).let { UiText.Plural(R.plurals.backup_exported, it, listOf(it)) } }

    fun importSkipped(uri: Uri) = backup { skippedBackup.import(uri).let { UiText.Plural(R.plurals.backup_imported, it, listOf(it)) } }

    private fun backup(action: suspend () -> UiText) {
        viewModelScope.launch {
            val message = try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: InvalidBackupException) {
                uiText(R.string.backup_invalid)
            } catch (_: Exception) {
                // IOException, SecurityException...: the file couldn't be written or read
                uiText(R.string.backup_file_error)
            }
            _messages.send(message)
        }
    }

    fun logout() = session.logout()
}
