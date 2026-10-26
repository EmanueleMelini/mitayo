package it.emanuelemelini.mitayo

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.emanuelemelini.mitayo.data.prefs.Settings
import it.emanuelemelini.mitayo.data.prefs.ThemeMode
import it.emanuelemelini.mitayo.data.telemetry.FirebaseSupport
import it.emanuelemelini.mitayo.ui.LaunchRequest
import it.emanuelemelini.mitayo.ui.MitayoNavHost
import it.emanuelemelini.mitayo.ui.POLICIES_VERSION
import it.emanuelemelini.mitayo.ui.PoliciesUpdateDialog
import it.emanuelemelini.mitayo.ui.login.LoginScreen
import it.emanuelemelini.mitayo.ui.privacy.PrivacyConsent
import it.emanuelemelini.mitayo.ui.theme.MitayoTheme
import it.emanuelemelini.mitayo.ui.update.UpdateDialogs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** AppCompatActivity (instead of ComponentActivity) so the in-app language works before Android 13. */
class MainActivity : AppCompatActivity() {

    private val container get() = (application as MitayoApp).container

    /** Where a tapped notification wants to go, until the navigation takes it. */
    private val launchRequests = MutableStateFlow<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Not on recreation: the redirect and the notification were already handled, the check already ran
        if (savedInstanceState == null) {
            handleLoginRedirect(intent)
            handleLaunchRequest(intent)
            container.appUpdater.checkAutomatically()
        } else {
            // A language change recreates the activity: the news topic and the statistics follow it
            container.applicationScope.launch { container.newsTopics.sync() }
        }
        container.telemetry.setLanguage()
        val firebase = FirebaseSupport.available(this)
        setContent {
            val settings: Settings? by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val loggedIn: Boolean? by container.session.isLoggedIn.collectAsStateWithLifecycle(initialValue = null)
            val launchRequest by launchRequests.collectAsStateWithLifecycle()
            // Until preferences and session are loaded (a few ms) the window background stays visible
            val current = settings ?: return@setContent
            val session = loggedIn ?: return@setContent
            val darkTheme = when (current.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
            }

            // Status and navigation bar icons follow the chosen theme, not the system one
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { darkTheme },
                )
                onDispose {}
            }

            MitayoTheme(darkTheme = darkTheme) {
                if (session) {
                    LaunchedEffect(Unit) { container.syncManager.syncIfStale() }
                    MitayoNavHost(launchRequest = launchRequest, onLaunchHandled = { launchRequests.value = null })
                } else {
                    LoginScreen()
                }
                // First the new terms to accept, then the statistics question (only with Firebase),
                // then the changelog and the updates
                val policiesPending = session && current.policiesAcceptedVersion < POLICIES_VERSION
                val consentPending = session && firebase && current.privacyConsentPending
                PrivacyConsent(pending = !policiesPending && consentPending, newsNotifications = current.newsNotifications)
                if (policiesPending) {
                    PoliciesUpdateDialog(
                        onAccept = {
                            container.applicationScope.launch {
                                container.settingsRepository.setPoliciesAccepted(POLICIES_VERSION)
                            }
                        },
                        onDecline = container.session::logout,
                    )
                } else if (!consentPending) {
                    UpdateDialogs()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLoginRedirect(intent)
        handleLaunchRequest(intent)
    }

    private fun handleLaunchRequest(intent: Intent?) {
        val request = LaunchRequest.from(intent) ?: return
        LaunchRequest.clear(intent)
        when (request) {
            LaunchRequest.ShowUpdate -> container.appUpdater.checkAutomatically(ignoreDailyLimit = true)
            else -> launchRequests.value = request
        }
    }

    private fun handleLoginRedirect(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "mitayo" && uri.host == "oauth") {
            container.session.handleRedirect(uri)
            // Consumed: a recreation must not send the same code again
            intent.data = null
        }
    }

    private companion object {
        // Same default scrims as enableEdgeToEdge()
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
