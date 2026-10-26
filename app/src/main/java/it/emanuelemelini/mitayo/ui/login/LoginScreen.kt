package it.emanuelemelini.mitayo.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.auth.LoginProblem
import it.emanuelemelini.mitayo.ui.AppLinks
import it.emanuelemelini.mitayo.ui.POLICIES_VERSION
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import it.emanuelemelini.mitayo.ui.openInBrowser
import it.emanuelemelini.mitayo.ui.theme.MitayoColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen() {
    val container = appContainer()
    val session = container.session
    val state by session.loginState.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var noBrowser by remember { mutableStateOf(false) }
    // Terms and privacy accepted before the sign-in (MAL API agreement 4(f)): already given for
    // this version, it stays ticked
    val acceptedBefore = (settings?.policiesAcceptedVersion ?: 0) >= POLICIES_VERSION
    var accepted by rememberSaveable(acceptedBefore) { mutableStateOf(acceptedBefore) }
    // The box can be ticked only after both documents were opened
    var openedTerms by rememberSaveable(acceptedBefore) { mutableStateOf(acceptedBefore) }
    var openedPrivacy by rememberSaveable(acceptedBefore) { mutableStateOf(acceptedBefore) }
    var consentProblem by remember { mutableStateOf<Int?>(null) }
    fun setAccepted(value: Boolean) {
        if (value && !(openedTerms && openedPrivacy)) {
            consentProblem = R.string.policies_open_first
            return
        }
        accepted = value
        consentProblem = null
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppMark(Modifier.staggeredEntrance(0).size(128.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.app_name_japanese),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.staggeredEntrance(1),
            )
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.staggeredEntrance(2),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.login_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.staggeredEntrance(3),
            )
            Spacer(Modifier.height(40.dp))
            if (state.working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.login_working))
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .staggeredEntrance(4)
                        .widthIn(max = 480.dp)
                        .fillMaxWidth()
                        .clickable { setAccepted(!accepted) },
                ) {
                    Checkbox(checked = accepted, onCheckedChange = ::setAccepted)
                    Text(
                        text = consentText(
                            onTerms = {
                                if (openInBrowser(context, AppLinks.terms)) openedTerms = true else noBrowser = true
                            },
                            onPrivacy = {
                                if (openInBrowser(context, AppLinks.privacy)) openedPrivacy = true else noBrowser = true
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        noBrowser = false
                        if (!accepted) {
                            consentProblem = R.string.login_privacy_missing
                            return@Button
                        }
                        scope.launch {
                            container.settingsRepository.setPoliciesAccepted(POLICIES_VERSION)
                            val uri = session.authorizeUri() ?: return@launch
                            noBrowser = !openInBrowser(context, uri)
                        }
                    },
                    // Not wider than a phone on tablets
                    modifier = Modifier.staggeredEntrance(5).widthIn(max = 480.dp).fillMaxWidth().height(56.dp),
                ) {
                    Text(stringResource(R.string.login_button), style = MaterialTheme.typography.titleMedium)
                }
            }
            val problem = when {
                consentProblem != null -> consentProblem
                noBrowser -> R.string.login_no_browser
                else -> when (state.problem) {
                    LoginProblem.MISSING_CLIENT_ID -> R.string.login_missing_client_id
                    LoginProblem.CANCELLED -> R.string.login_cancelled
                    LoginProblem.FAILED -> R.string.login_failed
                    LoginProblem.NETWORK -> R.string.error_network
                    null -> null
                }
            }
            if (problem != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(problem),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.login_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** "I've read and I accept the terms of use and the privacy policy", both as links. */
@Composable
private fun consentText(onTerms: () -> Unit, onPrivacy: () -> Unit): AnnotatedString {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val prefix = stringResource(R.string.login_consent_prefix)
    val terms = stringResource(R.string.login_consent_terms)
    val middle = stringResource(R.string.login_consent_middle)
    val privacy = stringResource(R.string.login_consent_privacy)
    return buildAnnotatedString {
        append(prefix)
        withLink(LinkAnnotation.Clickable("terms", linkStyles) { onTerms() }) { append(terms) }
        append(middle)
        withLink(LinkAnnotation.Clickable("privacy", linkStyles) { onPrivacy() }) { append(privacy) }
    }
}

/** The launcher icon drawn in the app (adaptive icons can't be loaded as a painter). */
@Composable
fun AppMark(modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(30))
            .background(MitayoColors.Murasaki)
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
