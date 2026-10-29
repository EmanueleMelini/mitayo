package it.emanuelemelini.mitayo.ui.anilist

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.SyncManager
import it.emanuelemelini.mitayo.data.anilist.AniListInfo
import it.emanuelemelini.mitayo.data.anilist.AniListSessionSummary
import it.emanuelemelini.mitayo.data.anilist.AniListSync
import it.emanuelemelini.mitayo.data.anilist.AniListSyncState
import it.emanuelemelini.mitayo.data.auth.LoginProblem
import it.emanuelemelini.mitayo.data.auth.LoginState
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.openInBrowser
import it.emanuelemelini.mitayo.ui.profile.Avatar
import it.emanuelemelini.mitayo.ui.profile.PageCard
import it.emanuelemelini.mitayo.ui.profile.clickableRow
import it.emanuelemelini.mitayo.ui.profile.formatTime
import it.emanuelemelini.mitayo.ui.toUiText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AniListUiState(
    val info: AniListInfo = AniListInfo(),
    val sync: AniListSyncState = AniListSyncState.Idle,
    val login: LoginState = LoginState(),
    /** Changes still to send to AniList. */
    val pending: Int = 0,
    /** Anime of the MAL list that AniList hasn't, still to look at. */
    val notOnAniList: Int = 0,
    /** The ones the user said "got it" to. */
    val acknowledged: Int = 0,
    /** The last session of the sync log. */
    val lastSession: AniListSessionSummary? = null,
)

/** What the AniList page shows of the "Not on AniList" page and of the log. */
private data class AniListExtras(val notOnAniList: Int, val acknowledged: Int, val lastSession: AniListSessionSummary?)

class AniListViewModel(private val aniListSync: AniListSync, private val syncManager: SyncManager) : ViewModel() {

    val uiState: StateFlow<AniListUiState> = combine(
        aniListSync.info,
        aniListSync.state,
        aniListSync.loginState,
        aniListSync.pendingCount,
        combine(aniListSync.notOnAniList, aniListSync.sessions) { missing, sessions ->
            AniListExtras(missing.count { !it.acknowledged }, missing.count { it.acknowledged }, sessions.firstOrNull())
        },
    ) { info, sync, login, pending, extras ->
        AniListUiState(info, sync, login, pending, extras.notOnAniList, extras.acknowledged, extras.lastSession)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AniListUiState())

    suspend fun authorizeUri(): Uri? = aniListSync.authorizeUri()

    /** The MAL list first, then AniList follows it. */
    fun syncNow() = syncManager.syncNow()

    fun unlink() = aniListSync.unlink()
}

/** Profile → AniList: the link to make, or the linked account with its sync. */
@Composable
fun AniListPage(onOpenNotOnAniList: () -> Unit, onOpenLog: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { AniListViewModel(container.aniListSync, container.syncManager) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val account = state.info.account
    if (account == null) {
        LinkCard(state, viewModel)
        return
    }
    var confirmUnlink by rememberSaveable { mutableStateOf(false) }
    val syncing = state.sync is AniListSyncState.Syncing
    PageCard {
        ListItem(
            leadingContent = { Avatar(account.name, account.avatar) },
            headlineContent = { Text(account.name) },
            supportingContent = { Text(stringResource(R.string.anilist_account, stringResource(scoreFormatRes(account.scoreFormat)))) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider()
        ListItem(
            leadingContent = { Icon(Icons.Rounded.Sync, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.anilist_sync_now)) },
            supportingContent = {
                val failed = state.sync as? AniListSyncState.Failed
                Text(
                    text = when {
                        failed != null -> failed.error.toUiText().asString()
                        syncing -> stringResource(R.string.anilist_syncing)
                        state.info.syncedAt > 0 -> stringResource(R.string.profile_last_sync, formatTime(state.info.syncedAt))
                        else -> stringResource(R.string.profile_never_synced)
                    },
                    color = if (failed != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            },
            trailingContent = { if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickableRow(enabled = !syncing, onClick = viewModel::syncNow),
        )
        if (state.pending > 0) {
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Rounded.CloudUpload, contentDescription = null) },
                headlineContent = { Text(pluralStringResource(R.plurals.anilist_pending, state.pending, state.pending)) },
                supportingContent = { Text(stringResource(R.string.anilist_pending_body)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (state.notOnAniList > 0 || state.acknowledged > 0) {
            HorizontalDivider()
            ListItem(
                leadingContent = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.anilist_not_on_anilist_title)) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            listOfNotNull(
                                state.notOnAniList.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_not_on_anilist, it, it) },
                                state.acknowledged.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_acknowledged_count, it, it) },
                            ).joinToString(" · "),
                            stringResource(R.string.anilist_adult_off_short).takeUnless { account.adultContent },
                        ).joinToString("\n")
                    )
                },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickableRow(onClick = onOpenNotOnAniList),
            )
        }
        if (state.info.notOnMal > 0) {
            HorizontalDivider()
            // No list: these have no MAL page to show
            ListItem(
                leadingContent = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.anilist_not_on_mal_title)) },
                supportingContent = { Text(pluralStringResource(R.plurals.anilist_not_on_mal, state.info.notOnMal, state.info.notOnMal)) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        HorizontalDivider()
        ListItem(
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.anilist_log_title)) },
            supportingContent = {
                Text(
                    state.lastSession?.let { last ->
                        stringResource(R.string.anilist_log_last, "${formatTime(last.session.startedAt)} · ${sessionSummaryText(last)}")
                    } ?: stringResource(R.string.anilist_log_empty_title)
                )
            },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickableRow(onClick = onOpenLog),
        )
    }
    PageCard {
        ListItem(
            leadingContent = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            headlineContent = { Text(stringResource(R.string.anilist_unlink), color = MaterialTheme.colorScheme.error) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickableRow { confirmUnlink = true },
        )
    }
    if (confirmUnlink) {
        UnlinkDialog(
            pending = state.pending,
            onConfirm = {
                confirmUnlink = false
                viewModel.unlink()
            },
            onDismiss = { confirmUnlink = false },
        )
    }
}

/** What the link does, and the button that starts it in the browser. */
@Composable
private fun LinkCard(state: AniListUiState, viewModel: AniListViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var noBrowser by remember { mutableStateOf(false) }
    PageCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.anilist_link_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.anilist_link_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val problem = when {
                noBrowser -> stringResource(R.string.anilist_no_browser)
                state.login.problem != null -> stringResource(
                    when (state.login.problem) {
                        LoginProblem.MISSING_CLIENT_ID -> R.string.anilist_missing_client_id
                        LoginProblem.CANCELLED -> R.string.login_cancelled
                        LoginProblem.FAILED -> R.string.anilist_login_failed
                        LoginProblem.NETWORK -> R.string.error_network
                    }
                )
                state.info.expired -> stringResource(R.string.anilist_expired)
                else -> null
            }
            problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !state.login.working,
                onClick = {
                    scope.launch {
                        val uri = viewModel.authorizeUri() ?: return@launch
                        noBrowser = !openInBrowser(context, uri)
                    }
                },
            ) {
                if (state.login.working) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.anilist_link_action))
                }
            }
        }
    }
}

/** Also from the conflicts: there the only way out besides choosing. */
@Composable
internal fun UnlinkDialog(pending: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.anilist_unlink_title)) },
        text = {
            Text(
                if (pending > 0) {
                    pluralStringResource(R.plurals.anilist_unlink_body_pending, pending, pending)
                } else {
                    stringResource(R.string.anilist_unlink_body)
                }
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.anilist_unlink), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
