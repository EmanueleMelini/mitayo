package it.emanuelemelini.mitayo.ui.profile

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.prefs.Settings
import it.emanuelemelini.mitayo.data.telemetry.FirebaseSupport
import it.emanuelemelini.mitayo.ui.AppLinks
import it.emanuelemelini.mitayo.ui.openInBrowser

/** Usage statistics and crash reports (Firebase), as answered after the first login. */
@Composable
internal fun PrivacyPage(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    val firebase = remember { FirebaseSupport.available(context) }
    Text(
        text = stringResource(if (firebase) R.string.privacy_never_sent else R.string.firebase_unavailable),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    PageCard {
        SwitchRow(
            title = stringResource(R.string.privacy_usage_stats),
            body = stringResource(R.string.privacy_usage_stats_hint),
            checked = firebase && settings.usageStats == true,
            onCheckedChange = viewModel::setUsageStats,
            enabled = firebase,
        )
        HorizontalDivider()
        SwitchRow(
            title = stringResource(R.string.privacy_crash_reports),
            body = stringResource(R.string.privacy_crash_reports_hint),
            checked = firebase && settings.crashReports == true,
            onCheckedChange = viewModel::setCrashReports,
            enabled = firebase,
        )
    }
    PageCard {
        LinkRow(Icons.Rounded.PrivacyTip, stringResource(R.string.profile_privacy), null) {
            openInBrowser(context, AppLinks.privacy)
        }
    }
}

/** "Statistics: yes · Crash reports: no", for the Profile menu. */
@Composable
internal fun privacySummary(settings: Settings): String {
    val context = LocalContext.current
    if (!remember { FirebaseSupport.available(context) }) return stringResource(R.string.firebase_unavailable_short)
    fun answer(value: Boolean?) = if (value == true) R.string.answer_yes else R.string.answer_no
    return stringResource(
        R.string.profile_summary_privacy,
        stringResource(answer(settings.usageStats)),
        stringResource(answer(settings.crashReports)),
    )
}
