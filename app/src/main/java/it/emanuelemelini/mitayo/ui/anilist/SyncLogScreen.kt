package it.emanuelemelini.mitayo.ui.anilist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.anilist.AniListSessionEvent
import it.emanuelemelini.mitayo.data.anilist.AniListSessionSummary
import it.emanuelemelini.mitayo.data.anilist.AniListSide
import it.emanuelemelini.mitayo.data.anilist.AniListStatus
import it.emanuelemelini.mitayo.data.anilist.AniListSync
import it.emanuelemelini.mitayo.data.anilist.ScoreFormat
import it.emanuelemelini.mitayo.data.anilist.SyncEventKind
import it.emanuelemelini.mitayo.data.anilist.SyncSessionKind
import it.emanuelemelini.mitayo.data.anilist.SyncSessionState
import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.ui.aniListErrorText
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.EmptyMessage
import it.emanuelemelini.mitayo.ui.components.listStatusLine
import it.emanuelemelini.mitayo.ui.profile.formatTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class SyncLogViewModel(aniListSync: AniListSync) : ViewModel() {
    /** null until loaded. */
    val sessions: StateFlow<List<AniListSessionSummary>?> =
        aniListSync.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Profile → AniList → the sync log: the sessions, the last first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogScreen(onBack: () -> Unit, onOpenSession: (Long) -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { SyncLogViewModel(container.aniListSync) }
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anilist_log_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        val list = sessions ?: return@Scaffold
        if (list.isEmpty()) {
            EmptyMessage(
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                title = stringResource(R.string.anilist_log_empty_title),
                body = stringResource(R.string.anilist_log_empty_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(list, key = { it.session.id }) { summary ->
                SessionCard(summary, onClick = { onOpenSession(summary.session.id) })
            }
        }
    }
}

data class SyncSessionUiState(
    val loaded: Boolean = false,
    /** null: no such session any more (the log keeps the last 30). */
    val summary: AniListSessionSummary? = null,
    val events: List<AniListSessionEvent> = emptyList(),
    /** The anime of the events still in the catalog, for the chosen titles. */
    val anime: Map<Int, Anime> = emptyMap(),
    val titles: TitleChoice = TitleChoice(),
    val format: ScoreFormat = ScoreFormat.POINT_10,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SyncSessionViewModel(aniListSync: AniListSync, settingsRepository: SettingsRepository, sessionId: Long) : ViewModel() {

    /** Live while the session runs: its events arrive as they happen. */
    val uiState: StateFlow<SyncSessionUiState> = combine(
        aniListSync.session(sessionId),
        aniListSync.events(sessionId).mapLatest { events -> events to aniListSync.anime(events.mapNotNull { it.malId }.distinct()) },
        settingsRepository.settings.map { it.titles }.distinctUntilChanged(),
        aniListSync.info.map { it.account?.scoreFormat ?: ScoreFormat.POINT_10 }.distinctUntilChanged(),
    ) { summary, (events, anime), titles, format ->
        SyncSessionUiState(loaded = true, summary = summary, events = events, anime = anime, titles = titles, format = format)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncSessionUiState())
}

/** One session of the log, step by step, the last step first. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSessionScreen(sessionId: Long, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel { SyncSessionViewModel(container.aniListSync, container.settingsRepository, sessionId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(summary?.let { sessionHeading(it) } ?: stringResource(R.string.anilist_log_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        if (!state.loaded) return@Scaffold
        if (summary == null) {
            EmptyMessage(
                icon = Icons.AutoMirrored.Rounded.ReceiptLong,
                title = stringResource(R.string.anilist_log_empty_title),
                body = stringResource(R.string.anilist_log_empty_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "summary") { SessionCard(summary, onClick = null, showHeading = false) }
            items(state.events, key = { it.id }) { event ->
                EventRow(event, state.anime[event.malId], state.titles, state.format)
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = dropUnlessResumed(block = onBack)) {
        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

/** A session: kind and start, its state (with the progress while it runs) and what it did. */
@Composable
private fun SessionCard(summary: AniListSessionSummary, onClick: (() -> Unit)?, showHeading: Boolean = true) {
    val session = summary.session
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(sessionIcon(summary), contentDescription = null, tint = sessionColor(summary))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (showHeading) sessionHeading(summary) else sessionStateText(summary),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
            }
            if (showHeading) {
                Text(sessionStateText(summary), style = MaterialTheme.typography.bodySmall, color = sessionColor(summary))
            }
            if (session.state == SyncSessionState.RUNNING) {
                val done = summary.toAniList + summary.failed
                val total = done + summary.remaining
                LinearProgressIndicator(
                    progress = { if (total > 0) done.toFloat() / total else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = sessionSummaryText(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick != null) {
        Card(onClick = onClick, shape = RoundedCornerShape(20.dp), colors = colors) { content() }
    } else {
        Card(shape = RoundedCornerShape(20.dp), colors = colors) { content() }
    }
}

/** "Sync · 28/10/26, 21:30". */
@Composable
private fun sessionHeading(summary: AniListSessionSummary): String = stringResource(
    R.string.anilist_session_heading,
    stringResource(if (summary.session.kind == SyncSessionKind.APP) R.string.anilist_session_app else R.string.anilist_session_sync),
    formatTime(summary.session.startedAt),
)

/** Running (how far), done at, or failed with why. */
@Composable
private fun sessionStateText(summary: AniListSessionSummary): String {
    val session = summary.session
    return when (session.state) {
        SyncSessionState.RUNNING -> {
            val done = summary.toAniList + summary.failed
            if (summary.remaining > 0) {
                stringResource(R.string.anilist_session_progress, done, done + summary.remaining)
            } else {
                stringResource(R.string.anilist_session_running)
            }
        }
        SyncSessionState.DONE -> stringResource(R.string.anilist_session_done, formatClock(session.endedAt ?: session.updatedAt))
        SyncSessionState.FAILED -> listOfNotNull(
            stringResource(R.string.anilist_session_failed),
            summary.errorCode?.let { aniListErrorText(it).asString() },
        ).joinToString(": ")
    }
}

/** "12 to AniList · 1 to MAL · 2 not on AniList…", or no changes. */
@Composable
internal fun sessionSummaryText(summary: AniListSessionSummary): String = listOfNotNull(
    summary.toAniList.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_summary_to_anilist, it, it) },
    summary.toMal.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_summary_to_mal, it, it) },
    summary.notFound.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_summary_not_found, it, it) },
    summary.found.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_summary_found, it, it) },
    summary.conflicts.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_summary_conflicts, it, it) },
    summary.failed.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_summary_failed, it, it) },
).joinToString(" · ").ifEmpty { stringResource(R.string.anilist_session_nothing) }

private fun sessionIcon(summary: AniListSessionSummary): ImageVector = when (summary.session.state) {
    SyncSessionState.RUNNING -> Icons.Rounded.CloudSync
    SyncSessionState.DONE -> Icons.Rounded.CloudDone
    SyncSessionState.FAILED -> Icons.Rounded.SyncProblem
}

@Composable
private fun sessionColor(summary: AniListSessionSummary): Color = when (summary.session.state) {
    SyncSessionState.RUNNING -> MaterialTheme.colorScheme.primary
    SyncSessionState.DONE -> MaterialTheme.colorScheme.onSurfaceVariant
    SyncSessionState.FAILED -> MaterialTheme.colorScheme.error
}

/** One step: what happened, to which anime, and when. */
@Composable
private fun EventRow(event: AniListSessionEvent, anime: Anime?, titles: TitleChoice, format: ScoreFormat) {
    val name = anime?.displayTitle(titles) ?: event.title ?: stringResource(R.string.anilist_event_unknown_anime, event.malId ?: 0)
    val total = anime?.episodes ?: 0
    val (text, detail) = when (event.kind) {
        SyncEventKind.LIST_READ -> pluralStringResource(R.plurals.anilist_event_list_read, event.count, event.count) to null
        SyncEventKind.LOOKED_UP -> pluralStringResource(R.plurals.anilist_event_looked_up, event.count, event.count) to null
        SyncEventKind.TO_ANILIST -> stringResource(R.string.anilist_event_to_anilist, name) to
            // AniList's own count of episodes may not be MAL's: no total
            AniListStatus.fromApi(event.status)?.let { aniListLine(AniListSide(it, event.episodes, event.score), 0, format) }
        SyncEventKind.REMOVED_FROM_ANILIST -> stringResource(R.string.anilist_event_removed_anilist, name) to null
        SyncEventKind.TO_MAL -> stringResource(R.string.anilist_event_to_mal, name) to
            ListStatus.fromApi(event.status)?.let { listStatusLine(it, event.episodes, total, event.score) }
        SyncEventKind.REMOVED_FROM_MAL -> stringResource(R.string.anilist_event_removed_mal, name) to null
        SyncEventKind.NOT_FOUND -> stringResource(R.string.anilist_event_not_found, name) to null
        SyncEventKind.FOUND -> stringResource(R.string.anilist_event_found, name) to null
        SyncEventKind.CONFLICT -> stringResource(R.string.anilist_event_conflict, name) to null
        SyncEventKind.SEND_FAILED -> stringResource(R.string.anilist_event_send_failed, name, event.code ?: 0) to null
        SyncEventKind.WAITING -> if (event.code == -1) {
            stringResource(R.string.anilist_event_waiting_network) to null
        } else {
            stringResource(R.string.anilist_event_waiting, event.code ?: 0) to null
        }
        SyncEventKind.ERROR -> stringResource(R.string.anilist_event_error, aniListErrorText(event.code ?: 0).asString()) to null
    }
    val warning = event.kind in setOf(SyncEventKind.SEND_FAILED, SyncEventKind.ERROR, SyncEventKind.CONFLICT)
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Icon(
            eventIcon(event),
            contentDescription = null,
            tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = formatClock(event.at, seconds = true),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun eventIcon(event: AniListSessionEvent): ImageVector = when (event.kind) {
    SyncEventKind.LIST_READ -> Icons.Rounded.CloudSync
    SyncEventKind.LOOKED_UP -> Icons.Rounded.Search
    SyncEventKind.TO_ANILIST -> Icons.Rounded.Upload
    SyncEventKind.TO_MAL -> Icons.Rounded.Download
    SyncEventKind.REMOVED_FROM_ANILIST, SyncEventKind.REMOVED_FROM_MAL -> Icons.Rounded.RemoveCircleOutline
    SyncEventKind.NOT_FOUND -> Icons.Rounded.LinkOff
    SyncEventKind.FOUND -> Icons.Rounded.Link
    SyncEventKind.CONFLICT -> Icons.AutoMirrored.Rounded.CompareArrows
    SyncEventKind.SEND_FAILED -> Icons.Rounded.ErrorOutline
    SyncEventKind.WAITING -> if (event.code == -1) Icons.Rounded.WifiOff else Icons.Rounded.HourglassTop
    SyncEventKind.ERROR -> Icons.Rounded.SyncProblem
}

/** The time of day, with the seconds for the steps of a session. */
private fun formatClock(epochMillis: Long, seconds: Boolean = false): String =
    DateTimeFormatter.ofLocalizedTime(if (seconds) FormatStyle.MEDIUM else FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
