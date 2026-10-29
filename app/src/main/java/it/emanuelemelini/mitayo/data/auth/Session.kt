package it.emanuelemelini.mitayo.data.auth

import android.net.Uri
import androidx.room.withTransaction
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.ListMirror
import it.emanuelemelini.mitayo.data.Outbox
import it.emanuelemelini.mitayo.data.SyncManager
import it.emanuelemelini.mitayo.data.db.AppDatabase
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

enum class LoginProblem { MISSING_CLIENT_ID, CANCELLED, FAILED, NETWORK }

data class LoginState(val working: Boolean = false, val problem: LoginProblem? = null)

/** Login and logout, with what follows them (first sync, pending changes, local data). */
class Session(
    private val auth: MalAuth,
    private val database: AppDatabase,
    private val outbox: Outbox,
    private val mirror: ListMirror,
    private val syncManager: SyncManager,
    private val settingsRepository: SettingsRepository,
    private val telemetry: Telemetry,
    private val scope: CoroutineScope,
) {
    val isLoggedIn: Flow<Boolean> = auth.isLoggedIn

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()

    /** The MAL page to open in a Custom Tab, or null when the app has no client ID. */
    suspend fun authorizeUri(): Uri? {
        if (!auth.hasClientId) {
            _loginState.value = LoginState(problem = LoginProblem.MISSING_CLIENT_ID)
            return null
        }
        _loginState.value = LoginState()
        return auth.authorizeUri()
    }

    /** The browser came back to mitayo://oauth. */
    fun handleRedirect(uri: Uri) {
        scope.launch {
            _loginState.value = LoginState(working = true)
            val outcome = try {
                auth.completeLogin(uri)
            } catch (_: IOException) {
                _loginState.value = LoginState(problem = LoginProblem.NETWORK)
                return@launch
            }
            _loginState.value = when (outcome) {
                LoginOutcome.SUCCESS -> {
                    telemetry.logLogin()
                    syncManager.syncNow()
                    // Changes made before the session expired are still waiting
                    outbox.schedule()
                    LoginState()
                }
                LoginOutcome.CANCELLED -> LoginState(problem = LoginProblem.CANCELLED)
                LoginOutcome.FAILED -> LoginState(problem = LoginProblem.FAILED)
            }
        }
    }

    /** Everything of this user goes (list, skipped, swipe history, unsent changes, the AniList link); the catalog stays. */
    fun logout() {
        scope.launch {
            syncManager.cancel()
            outbox.clear()
            mirror.clear()
            auth.logout()
            database.withTransaction {
                database.listDao().clear()
                database.skippedDao().clear()
                database.historyDao().clear()
                database.animeDao().clearRemoteList(Category.Suggested.key)
                database.animeDao().clearUserLists()
            }
            settingsRepository.clearUser(remoteListKeys = listOf(Category.Suggested.key))
        }
    }
}
