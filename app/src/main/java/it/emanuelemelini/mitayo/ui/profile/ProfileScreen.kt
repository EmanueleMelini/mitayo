package it.emanuelemelini.mitayo.ui.profile

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.mitayo.BuildConfig
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.update.UpdateState
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import it.emanuelemelini.mitayo.ui.theme.MitayoColors

/** A page of the Profile; the constant names are part of the navigation route. */
enum class ProfilePage(@StringRes val titleRes: Int, val icon: ImageVector) {
    APPEARANCE(R.string.profile_section_appearance, Icons.Rounded.Palette),
    CONTENT(R.string.profile_section_content, Icons.Rounded.Tune),
    NOTIFICATIONS(R.string.profile_section_notifications, Icons.Rounded.Notifications),
    /** Only when the build has the AniList client ID. */
    ANILIST(R.string.profile_section_anilist, Icons.Rounded.SyncAlt),
    DATA(R.string.profile_section_data, Icons.Rounded.Storage),
    PRIVACY(R.string.profile_section_privacy, Icons.Rounded.Shield),
    ABOUT(R.string.profile_section_about, Icons.Rounded.Info),
}

@Composable
internal fun profileViewModel(): ProfileViewModel {
    val container = appContainer()
    return viewModel {
        ProfileViewModel(
            container.settingsRepository,
            container.listRepository,
            container.deckRepository,
            container.outbox,
            container.syncManager,
            container.session,
            container.reminderScheduler,
            container.skippedBackup,
        )
    }
}

/**
 * Profile: the account with its numbers, then one row per page with what is set now. The rows
 * enter one after the other the first time the menu is shown.
 */
@Composable
fun ProfileScreen(onOpenPage: (ProfilePage) -> Unit, onOpenStats: () -> Unit) {
    val container = appContainer()
    val viewModel = profileViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by container.appUpdater.state.collectAsStateWithLifecycle()
    val aniList by container.aniListSync.info.collectAsStateWithLifecycle(initialValue = null)
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings

    // Only on the way in: coming back from a page the menu is already there
    val firstVisit = rememberSaveable { mutableStateOf(true) }
    val animateEntrance = remember { firstVisit.value }
    LaunchedEffect(Unit) { firstVisit.value = false }
    fun Modifier.entrance(index: Int) = if (animateEntrance) staggeredEntrance(index) else this

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
            Avatar(settings.userName, settings.userPicture)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(settings.userName ?: stringResource(R.string.nav_profile), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = stringResource(R.string.profile_account),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.entrance(1)) {
            StatCard(stringResource(R.string.profile_stat_completed), state.stats.completed, MitayoColors.Matcha, Modifier.weight(1f))
            StatCard(stringResource(R.string.profile_stat_skipped), state.stats.skipped, MitayoColors.Aka, Modifier.weight(1f))
        }

        PageCard(Modifier.entrance(1)) {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(R.string.stats_title)) },
                supportingContent = { Text(stringResource(R.string.stats_summary)) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickableRow(onClick = dropUnlessResumed(block = onOpenStats)),
            )
        }

        AnimatedVisibility(
            visible = state.stats.unsent > 0,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                ListItem(
                    leadingContent = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                    headlineContent = {
                        Text(pluralStringResource(R.plurals.profile_unsent, state.stats.unsent, state.stats.unsent))
                    },
                    supportingContent = { Text(stringResource(R.string.profile_unsent_body)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }

        PageCard {
            val summaries = mapOf(
                ProfilePage.APPEARANCE to stringResource(
                    R.string.profile_summary_appearance,
                    stringResource(themeLabelRes(settings.themeMode)),
                    stringResource(titleLabelRes(settings.titles.title)),
                    stringResource(settings.titles.subtitle?.let(::titleLabelRes) ?: R.string.subtitle_none),
                ),
                ProfilePage.CONTENT to stringResource(
                    R.string.profile_summary_content,
                    stringResource(if (settings.showNsfw) R.string.profile_summary_nsfw_on else R.string.profile_summary_nsfw_off),
                    stringResource(affinityLabelRes(settings.affinityMethod)),
                ),
                ProfilePage.NOTIFICATIONS to notificationsSummary(settings),
                ProfilePage.ANILIST to when {
                    aniList?.account != null -> stringResource(R.string.profile_summary_anilist_linked, aniList?.account?.name.orEmpty())
                    aniList?.expired == true -> stringResource(R.string.profile_summary_anilist_expired)
                    else -> stringResource(R.string.profile_summary_anilist_off)
                },
                ProfilePage.PRIVACY to privacySummary(settings),
                ProfilePage.DATA to if (state.syncInfo.listSyncedAt > 0) {
                    stringResource(R.string.profile_last_sync, formatTime(state.syncInfo.listSyncedAt))
                } else {
                    stringResource(R.string.profile_never_synced)
                },
                ProfilePage.ABOUT to if (updateState is UpdateState.Available) {
                    stringResource(R.string.profile_summary_update, BuildConfig.VERSION_NAME)
                } else {
                    stringResource(R.string.profile_version, BuildConfig.VERSION_NAME)
                },
            )
            ProfilePage.entries.filter { it != ProfilePage.ANILIST || container.aniListSync.enabled }.forEachIndexed { index, page ->
                if (index > 0) HorizontalDivider()
                ListItem(
                    leadingContent = { Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(page.titleRes)) },
                    supportingContent = { Text(summaries.getValue(page), maxLines = 2) },
                    trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .entrance(index + 2)
                        .clickableRow(onClick = dropUnlessResumed { onOpenPage(page) }),
                )
            }
        }

        PageCard {
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                headlineContent = { Text(stringResource(R.string.profile_logout), color = MaterialTheme.colorScheme.error) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.entrance(ProfilePage.entries.size + 2).clickableRow { confirmLogout = true },
            )
        }

        Text(
            text = stringResource(R.string.profile_footer, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.profile_logout_title)) },
            text = {
                Text(
                    if (state.stats.unsent > 0) {
                        pluralStringResource(R.plurals.profile_logout_body_unsent, state.stats.unsent, state.stats.unsent)
                    } else {
                        stringResource(R.string.profile_logout_body)
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                }) { Text(stringResource(R.string.profile_logout), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}
