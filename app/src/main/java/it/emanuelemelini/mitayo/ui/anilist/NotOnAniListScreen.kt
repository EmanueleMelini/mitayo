package it.emanuelemelini.mitayo.ui.anilist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.CatalogRepository
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.StatusEditor
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.anilist.AniListSync
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.AnimeRow
import it.emanuelemelini.mitayo.ui.components.EmptyMessage
import it.emanuelemelini.mitayo.ui.deck.AnimeDetailsController
import it.emanuelemelini.mitayo.ui.deck.AnimeDetailsHost
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NotOnAniListUiState(
    val loaded: Boolean = false,
    /** Still to look at. */
    val cards: List<DeckCard> = emptyList(),
    /** "Got it": out of the way, under "Already checked". */
    val acknowledged: List<DeckCard> = emptyList(),
    /** 18+ anime left out while the setting hides them. */
    val hidden: Int = 0,
    /** AniList's 18+ content is off: AniList hides the adult anime from Mitayo too. */
    val aniListAdultOff: Boolean = false,
    val titles: TitleChoice = TitleChoice(),
)

class NotOnAniListViewModel(
    private val aniListSync: AniListSync,
    settingsRepository: SettingsRepository,
    catalogRepository: CatalogRepository,
    listRepository: ListRepository,
    deckRepository: DeckRepository,
    statusEditor: StatusEditor,
    telemetry: Telemetry,
) : ViewModel() {

    /** The same details sheet as the decks, from a tapped anime. */
    val details = AnimeDetailsController(viewModelScope, catalogRepository, listRepository, deckRepository, statusEditor, telemetry)

    val uiState: StateFlow<NotOnAniListUiState> = combine(
        aniListSync.notOnAniList,
        settingsRepository.settings.map { it.titles to it.showNsfw }.distinctUntilChanged(),
        aniListSync.info.map { it.account?.adultContent == false }.distinctUntilChanged(),
    ) { all, (titles, showNsfw), aniListAdultOff ->
        // 18+ anime stay out of sight here too while the setting is off
        val visible = if (showNsfw) all else all.filterNot { it.card.anime.nsfw }
        val (acknowledged, cards) = visible.partition { it.acknowledged }
        NotOnAniListUiState(
            loaded = true,
            cards = cards.map { it.card },
            acknowledged = acknowledged.map { it.card },
            hidden = all.size - visible.size,
            aniListAdultOff = aniListAdultOff,
            titles = titles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotOnAniListUiState())

    fun acknowledge(card: DeckCard) {
        viewModelScope.launch { aniListSync.acknowledge(card.anime.id) }
    }

    fun unacknowledge(card: DeckCard) {
        viewModelScope.launch { aniListSync.unacknowledge(card.anime.id) }
    }
}

/** Profile → AniList → the anime of the MAL list that AniList hasn't: they stay on MAL only. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotOnAniListScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel = viewModel {
        NotOnAniListViewModel(
            container.aniListSync,
            container.settingsRepository,
            container.catalogRepository,
            container.listRepository,
            container.deckRepository,
            container.statusEditor,
            container.telemetry,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAcknowledged by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anilist_not_on_anilist_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.loaded && state.cards.isEmpty() && state.acknowledged.isEmpty() && state.hidden == 0) {
            EmptyMessage(
                icon = Icons.Rounded.TaskAlt,
                title = stringResource(R.string.anilist_not_on_anilist_empty),
                body = null,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "intro") {
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.anilist_not_on_anilist_body),
                        stringResource(R.string.anilist_acknowledge_body),
                        stringResource(R.string.anilist_adult_off).takeIf { state.aniListAdultOff },
                        state.hidden.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.anilist_not_on_anilist_hidden, it, it) },
                    ).joinToString("\n\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
            // Between the texts and the list: closed, it's only its title
            if (state.acknowledged.isNotEmpty()) {
                item(key = "acknowledged") {
                    AcknowledgedHeader(
                        count = state.acknowledged.size,
                        open = showAcknowledged,
                        onToggle = { showAcknowledged = !showAcknowledged },
                        modifier = Modifier.animateItem(),
                    )
                }
                if (showAcknowledged) {
                    items(state.acknowledged, key = { "acknowledged-${it.anime.id}" }) { card ->
                        AnimeRow(
                            card = card,
                            titles = state.titles,
                            onSetStatus = null,
                            onOpenDetails = { viewModel.details.open(card) },
                            modifier = Modifier.animateItem().padding(start = 16.dp),
                            action = {
                                TextButton(onClick = { viewModel.unacknowledge(card) }) {
                                    Text(stringResource(R.string.anilist_unacknowledge))
                                }
                            },
                        )
                    }
                }
            }
            items(state.cards, key = { it.anime.id }) { card ->
                AnimeRow(
                    card = card,
                    titles = state.titles,
                    onSetStatus = null,
                    onOpenDetails = { viewModel.details.open(card) },
                    modifier = Modifier.animateItem(),
                    action = {
                        FilledTonalButton(onClick = { viewModel.acknowledge(card) }) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.anilist_acknowledge))
                        }
                    },
                )
            }
        }
    }

    AnimeDetailsHost(viewModel.details, state.titles)
}

/** "› Already checked (2)", turning to "⌄" when open. */
@Composable
private fun AcknowledgedHeader(count: Int, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(if (open) 90f else 0f, label = "acknowledged")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.rotate(rotation),
        )
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.anilist_acknowledged_section, count), style = MaterialTheme.typography.titleSmall)
    }
}
