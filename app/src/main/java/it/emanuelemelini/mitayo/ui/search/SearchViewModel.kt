package it.emanuelemelini.mitayo.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.CatalogRepository
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.StatusEditor
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.CatalogMatch
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.ui.UiText
import it.emanuelemelini.mitayo.ui.components.UndoMessage
import it.emanuelemelini.mitayo.ui.components.listStatusRes
import it.emanuelemelini.mitayo.ui.deck.AnimeDetailsController
import it.emanuelemelini.mitayo.ui.toUiText
import it.emanuelemelini.mitayo.ui.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The search on MyAnimeList, for the anime the catalog doesn't have. */
sealed interface MalSearch {
    data object Idle : MalSearch
    data object Searching : MalSearch
    /** [found]: how many anime MAL returned (now in the catalog, so among the results). */
    data class Done(val found: Int) : MalSearch
    data class Failed(val error: UiText) : MalSearch
}

data class SearchUiState(
    val query: String = "",
    /** null while there's nothing to search. */
    val results: List<CatalogMatch>? = null,
    val titles: TitleChoice = TitleChoice(),
    val malSearch: MalSearch = MalSearch.Idle,
) {
    /** MAL searches only from 3 characters. */
    val canSearchMal: Boolean get() = query.trim().length >= MAL_MIN_QUERY && malSearch != MalSearch.Searching

    companion object {
        const val MAL_MIN_QUERY = 3
    }
}

/** Any anime of the local catalog by title, to set its MAL status without swiping. */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    deckRepository: DeckRepository,
    private val catalogRepository: CatalogRepository,
    private val statusEditor: StatusEditor,
    settingsRepository: SettingsRepository,
    private val telemetry: Telemetry,
    listRepository: ListRepository,
) : ViewModel() {

    /** The same details sheet as the decks, from the poster of a result. */
    val details = AnimeDetailsController(viewModelScope, catalogRepository, listRepository, statusEditor, telemetry)

    private val query = MutableStateFlow("")
    private val malSearch = MutableStateFlow<MalSearch>(MalSearch.Idle)
    private var malSearchJob: Job? = null
    private val settings = settingsRepository.settings.map { it.titles to it.showNsfw }.distinctUntilChanged()

    private val results: Flow<List<CatalogMatch>?> = combine(
        // A query per pause in typing, not per letter
        query.debounce(SEARCH_DEBOUNCE_MS).map { it.trim() }.distinctUntilChanged(),
        settings.map { it.second }.distinctUntilChanged(),
    ) { text, showNsfw -> text to showNsfw }
        .flatMapLatest { (text, showNsfw) -> if (text.isEmpty()) flowOf(null) else deckRepository.search(text, showNsfw) }

    val uiState: StateFlow<SearchUiState> = combine(query, results, settings, malSearch) { query, results, (titles, _), mal ->
        SearchUiState(query, results, titles, mal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    private val _messages = Channel<UndoMessage>(Channel.BUFFERED)
    val messages: Flow<UndoMessage> = _messages.receiveAsFlow()

    fun setQuery(text: String) {
        if (text.trim() != query.value.trim()) {
            // The result of a MAL search belongs to the text it was for
            malSearchJob?.cancel()
            malSearch.value = MalSearch.Idle
        }
        query.value = text
    }

    /** Only on the user's tap: one request to MAL, what it finds joins the results. */
    fun searchOnMal() {
        val text = query.value.trim()
        if (text.length < SearchUiState.MAL_MIN_QUERY || malSearch.value == MalSearch.Searching) return
        malSearch.value = MalSearch.Searching
        telemetry.logMalSearch()
        malSearchJob = viewModelScope.launch {
            malSearch.value = try {
                MalSearch.Done(catalogRepository.searchMal(text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MalSearch.Failed(e.toUiText())
            }
        }
    }

    /** On MAL ([episodes] for watching); a skipped anime leaves the skipped ones (StatusEditor). */
    fun setStatus(match: CatalogMatch, status: ListStatus, episodes: Int = 0) {
        val anime = match.card.anime
        viewModelScope.launch {
            val undo = statusEditor.setStatus(anime, status, episodes)
            telemetry.logSearchSetStatus()
            val title = anime.displayTitle(uiState.value.titles)
            _messages.send(
                UndoMessage(anime.id, uiText(R.string.skipped_status_set, title, uiText(listStatusRes(status)))) {
                    viewModelScope.launch { undo() }
                }
            )
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
    }
}
