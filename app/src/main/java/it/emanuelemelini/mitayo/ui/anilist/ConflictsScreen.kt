package it.emanuelemelini.mitayo.ui.anilist

import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.anilist.AniListConflict
import it.emanuelemelini.mitayo.data.anilist.AniListSync
import it.emanuelemelini.mitayo.data.anilist.ListSide
import it.emanuelemelini.mitayo.data.anilist.ScoreFormat
import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.listStatusLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A conflict with its anime, when it's already downloaded from MAL. */
data class ConflictItem(val conflict: AniListConflict, val anime: Anime?)

data class ConflictsUiState(
    val items: List<ConflictItem> = emptyList(),
    val format: ScoreFormat = ScoreFormat.POINT_10,
    val titles: TitleChoice = TitleChoice(),
    val pending: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ConflictsViewModel(private val aniListSync: AniListSync, settingsRepository: SettingsRepository) : ViewModel() {

    private val items = aniListSync.conflicts.mapLatest { conflicts ->
        val anime = aniListSync.anime(conflicts.map { it.malId })
        conflicts.map { ConflictItem(it, anime[it.malId]) }
    }

    val uiState: StateFlow<ConflictsUiState> = combine(
        items,
        aniListSync.info,
        settingsRepository.settings,
        aniListSync.pendingCount,
    ) { items, info, settings, pending ->
        ConflictsUiState(items, info.account?.scoreFormat ?: ScoreFormat.POINT_10, settings.titles, pending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConflictsUiState())

    fun keep(malId: Int, side: ListSide) {
        viewModelScope.launch { aniListSync.resolve(malId, side) }
    }

    fun keepAll(side: ListSide) {
        viewModelScope.launch { aniListSync.resolveAll(side) }
    }

    fun unlink() = aniListSync.unlink()
}

/**
 * MAL and AniList disagree: the app waits here, in place of the decks, until every anime has its
 * list chosen (or AniList is unlinked).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictsScreen() {
    val container = appContainer()
    val viewModel = viewModel { ConflictsViewModel(container.aniListSync, container.settingsRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmUnlink by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.conflicts_title)) }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.conflicts_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { viewModel.keepAll(ListSide.MAL) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.conflicts_all_mal), maxLines = 1)
                        }
                        OutlinedButton(onClick = { viewModel.keepAll(ListSide.ANILIST) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.conflicts_all_anilist), maxLines = 1)
                        }
                    }
                }
            }
            items(state.items, key = { it.conflict.malId }) { item ->
                ConflictCard(
                    item = item,
                    format = state.format,
                    titles = state.titles,
                    onKeep = { viewModel.keep(item.conflict.malId, it) },
                    modifier = Modifier.animateItem(),
                )
            }
            item {
                TextButton(onClick = { confirmUnlink = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.anilist_unlink), color = MaterialTheme.colorScheme.error)
                }
            }
        }
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

@Composable
private fun ConflictCard(
    item: ConflictItem,
    format: ScoreFormat,
    titles: TitleChoice,
    onKeep: (ListSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    val conflict = item.conflict
    val anime = item.anime
    val totalEpisodes = anime?.episodes ?: 0
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp)) {
            AsyncImage(
                model = anime?.pictureUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 60.dp, height = 86.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = anime?.displayTitle(titles) ?: conflict.title ?: stringResource(R.string.conflicts_unknown_title, conflict.malId),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SideLine(
                    label = stringResource(R.string.conflicts_mal),
                    text = conflict.mal?.let { listStatusLine(it.status, it.episodes, totalEpisodes, it.score) }
                        ?: stringResource(R.string.conflicts_not_in_list),
                )
                SideLine(
                    label = stringResource(R.string.conflicts_anilist),
                    text = conflict.aniList?.let { aniListLine(it, totalEpisodes, format) }
                        ?: stringResource(R.string.conflicts_not_in_list),
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        ) {
            OutlinedButton(onClick = { onKeep(ListSide.MAL) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.conflicts_keep_mal), maxLines = 1)
            }
            OutlinedButton(onClick = { onKeep(ListSide.ANILIST) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.conflicts_keep_anilist), maxLines = 1)
            }
        }
    }
}

@Composable
private fun SideLine(label: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(56.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}
