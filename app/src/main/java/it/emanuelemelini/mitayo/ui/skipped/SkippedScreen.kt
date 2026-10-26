package it.emanuelemelini.mitayo.ui.skipped

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.AnimeRow
import it.emanuelemelini.mitayo.ui.components.EmptyMessage
import it.emanuelemelini.mitayo.ui.components.UndoMessagesEffect
import it.emanuelemelini.mitayo.ui.components.listStatusIcon
import it.emanuelemelini.mitayo.ui.components.listStatusLabel
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import it.emanuelemelini.mitayo.ui.deck.AnimeDetailsHost
import it.emanuelemelini.mitayo.ui.deck.EpisodesDialog
import it.emanuelemelini.mitayo.ui.deck.EpisodesPrompt
import it.emanuelemelini.mitayo.ui.deck.SwipeDirection

/** The skipped anime, searchable: one at a time back in the decks, or straight on MAL. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkippedScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: SkippedViewModel = viewModel {
        SkippedViewModel(
            container.deckRepository,
            container.statusEditor,
            container.settingsRepository,
            container.telemetry,
            container.catalogRepository,
            container.listRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    var confirmRestoreAll by rememberSaveable { mutableStateOf(false) }
    var episodesForId by rememberSaveable { mutableStateOf<Int?>(null) }
    // An undone anime goes back to its place: the list scrolls there when it's out of sight
    var revealId by remember { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    UndoMessagesEffect(viewModel.messages, snackbarHostState) { revealId = it.animeId }

    // Keyed on the list only: clearing revealId mustn't cancel the scroll
    LaunchedEffect(state.cards) {
        val index = state.cards.indexOfFirst { it.anime.id == revealId }
        if (index < 0) return@LaunchedEffect
        revealId = null
        withFrameNanos { } // the list lays out the row first
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index && it.offset >= 0 }
        if (!visible) listState.animateScrollToItem(index)
    }

    // Only on the way in, like the categories
    val firstVisit = rememberSaveable { mutableStateOf(true) }
    val animateEntrance = remember { firstVisit.value }
    LaunchedEffect(Unit) { firstVisit.value = false }
    val entrance: Modifier.(Int) -> Modifier = { index -> if (animateEntrance) staggeredEntrance(index) else this }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.skipped_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.total > 0) {
                        IconButton(onClick = { confirmRestoreAll = true }) {
                            Icon(Icons.Rounded.Restore, contentDescription = stringResource(R.string.skipped_restore_all))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loaded && state.total == 0) {
                EmptyMessage(
                    icon = Icons.Rounded.VisibilityOff,
                    title = stringResource(R.string.skipped_empty_title),
                    body = stringResource(R.string.skipped_empty_body),
                    modifier = Modifier.entrance(0),
                )
                return@Column
            }
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.skipped_search)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.skipped_search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .entrance(0),
            )
            if (state.filters.isNotEmpty()) {
                StatusFilters(
                    filters = state.filters,
                    selected = state.filter,
                    onSelect = viewModel::setFilter,
                    modifier = Modifier.entrance(1),
                )
            }
            if (state.loaded) {
                Text(
                    text = if (state.query.isBlank() && state.filter == StatusFilter.All) {
                        pluralStringResource(R.plurals.profile_skipped_count, state.total, state.total)
                    } else {
                        pluralStringResource(R.plurals.skipped_results, state.cards.size, state.cards.size, state.total)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp).entrance(1),
                )
            }
            if (state.loaded && state.cards.isEmpty()) {
                EmptyMessage(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.skipped_no_results, state.query.trim()),
                    body = null,
                )
                return@Column
            }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.cards, key = { _, card -> card.anime.id }) { index, card ->
                    AnimeRow(
                        card = card,
                        titles = state.titles,
                        onSetStatus = { status ->
                            if (status == ListStatus.WATCHING) episodesForId = card.anime.id else viewModel.setStatus(card, status)
                        },
                        // A row that leaves the list closes the gap smoothly
                        modifier = Modifier.animateItem().entrance(2 + index),
                        onRestore = { viewModel.restore(card) },
                        onOpenDetails = { viewModel.details.open(card) },
                    )
                }
            }
        }
    }

    AnimeDetailsHost(viewModel.details, state.titles)

    state.cards.find { it.anime.id == episodesForId }?.let { card ->
        EpisodesDialog(
            prompt = EpisodesPrompt(card, SwipeDirection.UP),
            titles = state.titles,
            onConfirm = { episodes ->
                viewModel.setStatus(card, ListStatus.WATCHING, episodes)
                episodesForId = null
            },
            onDismiss = { episodesForId = null },
        )
    }

    if (confirmRestoreAll) {
        AlertDialog(
            onDismissRequest = { confirmRestoreAll = false },
            title = { Text(stringResource(R.string.profile_restore_title)) },
            text = { Text(pluralStringResource(R.plurals.profile_restore_body, state.total, state.total)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.restoreAll()
                    confirmRestoreAll = false
                }) { Text(stringResource(R.string.profile_restore_confirm)) }
            },
            dismissButton = { TextButton(onClick = { confirmRestoreAll = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

/** All, not in the list, then one chip per status the skipped anime have, with how many. */
@Composable
private fun StatusFilters(
    filters: List<Pair<StatusFilter, Int>>,
    selected: StatusFilter,
    onSelect: (StatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(filters, key = { (filter, _) -> filter.toString() }) { (filter, count) ->
            val label = when (filter) {
                StatusFilter.All -> stringResource(R.string.skipped_filter_all)
                StatusFilter.NotInList -> stringResource(R.string.skipped_filter_not_in_list)
                is StatusFilter.Of -> listStatusLabel(filter.status)
            }
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(R.string.skipped_filter_count, label, count)) },
                leadingIcon = (filter as? StatusFilter.Of)?.let { status ->
                    { Icon(listStatusIcon(status.status), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                },
            )
        }
    }
}
