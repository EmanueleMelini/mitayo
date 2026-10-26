package it.emanuelemelini.mitayo.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.AnimeRow
import it.emanuelemelini.mitayo.ui.components.EmptyMessage
import it.emanuelemelini.mitayo.ui.components.UndoMessagesEffect
import it.emanuelemelini.mitayo.ui.deck.AnimeDetailsHost
import it.emanuelemelini.mitayo.ui.deck.EpisodesDialog
import it.emanuelemelini.mitayo.ui.deck.EpisodesPrompt
import it.emanuelemelini.mitayo.ui.deck.SwipeDirection

/** Search from the categories: any downloaded anime by title, with its MAL status to set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: SearchViewModel = viewModel {
        SearchViewModel(
            container.deckRepository,
            container.catalogRepository,
            container.statusEditor,
            container.settingsRepository,
            container.telemetry,
            container.listRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var episodesForId by rememberSaveable { mutableStateOf<Int?>(null) }

    UndoMessagesEffect(viewModel.messages, snackbarHostState)

    // The keyboard opens with the page, not again after a rotation
    var focused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!focused) {
            focusRequester.requestFocus()
            focused = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = { Text(stringResource(R.string.search_hint)) },
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
                    .focusRequester(focusRequester),
            )
            val results = state.results
            when {
                state.query.isBlank() -> EmptyMessage(
                    icon = Icons.Rounded.Search,
                    title = stringResource(R.string.search_empty_title),
                    body = stringResource(R.string.search_empty_body),
                )
                results == null -> Unit
                results.isEmpty() -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    EmptyMessage(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.search_no_results, state.query.trim()),
                        body = stringResource(R.string.search_no_results_body),
                    )
                    MalSearchFooter(state, onSearch = viewModel::searchOnMal)
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(results, key = { it.card.anime.id }) { match ->
                        AnimeRow(
                            card = match.card,
                            titles = state.titles,
                            onSetStatus = { status ->
                                if (status == ListStatus.WATCHING) episodesForId = match.card.anime.id else viewModel.setStatus(match, status)
                            },
                            modifier = Modifier.animateItem(),
                            skipped = match.skipped,
                            onOpenDetails = { viewModel.details.open(match.card) },
                        )
                    }
                    if (results.size >= DeckRepository.SEARCH_LIMIT) {
                        item(key = "limited") {
                            Text(
                                text = pluralStringResource(R.plurals.search_limited, results.size, results.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                            )
                        }
                    }
                    item(key = "mal") { MalSearchFooter(state, onSearch = viewModel::searchOnMal) }
                }
            }
        }
    }

    AnimeDetailsHost(viewModel.details, state.titles)

    state.results?.find { it.card.anime.id == episodesForId }?.let { match ->
        EpisodesDialog(
            prompt = EpisodesPrompt(match.card, SwipeDirection.UP),
            titles = state.titles,
            onConfirm = { episodes ->
                viewModel.setStatus(match, ListStatus.WATCHING, episodes)
                episodesForId = null
            },
            onDismiss = { episodesForId = null },
        )
    }
}

/**
 * Below the results: the anime isn't among the downloaded ones? One search on MyAnimeList, only
 * on a tap, and what it finds joins the results.
 */
@Composable
private fun MalSearchFooter(state: SearchUiState, onSearch: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        when (val mal = state.malSearch) {
            MalSearch.Idle -> Unit
            MalSearch.Searching -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            is MalSearch.Done -> Text(
                text = if (mal.found == 0) {
                    stringResource(R.string.search_mal_none)
                } else {
                    pluralStringResource(R.plurals.search_mal_found, mal.found, mal.found)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            is MalSearch.Failed -> Text(
                text = mal.error.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        if (state.malSearch !is MalSearch.Done) {
            OutlinedButton(onClick = onSearch, enabled = state.canSearchMal) {
                Icon(Icons.Rounded.TravelExplore, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.search_mal))
            }
            if (state.query.trim().length < SearchUiState.MAL_MIN_QUERY) {
                Text(
                    text = pluralStringResource(R.plurals.search_mal_min, SearchUiState.MAL_MIN_QUERY, SearchUiState.MAL_MIN_QUERY),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

