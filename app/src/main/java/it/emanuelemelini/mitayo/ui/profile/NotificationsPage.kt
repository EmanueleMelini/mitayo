package it.emanuelemelini.mitayo.ui.profile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.prefs.Settings as AppSettings
import it.emanuelemelini.mitayo.data.reminders.ReminderConfig
import it.emanuelemelini.mitayo.data.reminders.ReminderType
import it.emanuelemelini.mitayo.data.reminders.needsNotificationPermission
import it.emanuelemelini.mitayo.data.reminders.notificationsAllowed
import it.emanuelemelini.mitayo.data.telemetry.FirebaseSupport
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Mitayo news (push, from Firebase) and the local reminders with their time; asks for the
 * notification permission when one of them is turned on.
 */
@Composable
internal fun NotificationsPage(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(notificationsAllowed(context)) }
    var editing by remember { mutableStateOf<ReminderConfig?>(null) }
    val firebase = remember { FirebaseSupport.available(context) }

    // Checked again when coming back from the system settings
    LifecycleResumeEffect(Unit) {
        allowed = notificationsAllowed(context)
        onPauseOrDispose {}
    }
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        allowed = notificationsAllowed(context)
    }
    fun askPermissionIfNeeded() {
        if (needsNotificationPermission(context)) permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (!allowed && ((firebase && settings.newsNotifications) || settings.reminders.values.any { it.enabled })) {
        PageCard {
            ListItem(
                headlineContent = { Text(stringResource(R.string.notifications_off), color = MaterialTheme.colorScheme.error) },
                trailingContent = {
                    TextButton(onClick = { openNotificationSettings(context) }) { Text(stringResource(R.string.notifications_enable)) }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    PageCard {
        // Not a reminder: sent when there is something new, at no set time
        SwitchRow(
            title = stringResource(R.string.notifications_news),
            body = stringResource(if (firebase) R.string.notifications_news_hint else R.string.firebase_unavailable),
            checked = firebase && settings.newsNotifications,
            onCheckedChange = { enabled ->
                viewModel.setNewsNotifications(enabled)
                if (enabled) askPermissionIfNeeded()
            },
            enabled = firebase,
        )
    }

    PageCard {
        ReminderType.entries.forEachIndexed { index, type ->
            if (index > 0) HorizontalDivider()
            val config = settings.reminders.getValue(type)
            ListItem(
                headlineContent = { Text(stringResource(type.labelRes)) },
                supportingContent = { Text(stringResource(type.hintRes)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { editing = config }, enabled = config.enabled) {
                            Text(config.time.format(timeFormatter()))
                        }
                        Switch(
                            checked = config.enabled,
                            onCheckedChange = { enabled ->
                                viewModel.setReminderEnabled(type, enabled)
                                if (enabled) askPermissionIfNeeded()
                            },
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }

    editing?.let { config ->
        TimePickerDialog(
            initial = config.time,
            onConfirm = { time ->
                viewModel.setReminderTime(config.type, time)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** "News on · 2 reminders on", for the Profile menu; without Firebase only the reminders. */
@Composable
internal fun notificationsSummary(settings: AppSettings): String {
    val context = LocalContext.current
    val firebase = remember { FirebaseSupport.available(context) }
    val reminders = settings.reminders.values.count { it.enabled }
    return listOfNotNull(
        if (!firebase) null
        else stringResource(if (settings.newsNotifications) R.string.profile_summary_news_on else R.string.profile_summary_news_off),
        if (reminders == 0) stringResource(R.string.profile_summary_no_reminders)
        else pluralStringResource(R.plurals.profile_summary_reminders, reminders, reminders),
    ).joinToString(" · ").replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initial: LocalTime, onConfirm: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.notifications_time)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun timeFormatter(): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(LocalConfiguration.current.locales[0])

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}
