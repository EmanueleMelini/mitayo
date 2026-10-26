package it.emanuelemelini.mitayo.ui.skipped

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.CatalogRepository
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.StatusEditor
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.TitleLanguage
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.telemetry.SkippedAction
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.ui.components.UndoMessage
import it.emanuelemelini.mitayo.ui.components.listStatusRes
import it.emanuelemelini.mitayo.ui.deck.AnimeDetailsController
import it.emanuelemelini.mitayo.ui.uiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SkippedUiState(
    val loaded: Boolean = false,
    /** The skipped anime that match [query], the last skipped first. */
    val cards: List<DeckCard> = emptyList(),
    /** Every skipped anime, whatever the search. */
    val total: Int = 0,
    val query: String = "",
    val titles: TitleChoice = TitleChoice(),
)

class SkippedViewModel(
    private val deckRepository: DeckRepository,
    private val statusEditor: StatusEditor,
    settingsRepository: SettingsRepository,
    private val telemetry: Telemetry,
    catalogRepository: CatalogRepository,
    listRepository: ListRepository,
) : ViewModel() {

    /** The same details sheet as the decks, from the poster of a skipped anime. */
    val details = AnimeDetailsController(viewModelScope, catalogRepository, listRepository, statusEditor, telemetry)

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SkippedUiState> = combine(
        deckRepository.skipped,
        query,
        settingsRepository.settings.map { it.titles to it.showNsfw }.distinctUntilChanged(),
    ) { skipped, query, (titles, showNsfw) ->
        // 18+ anime stay out of sight here too while the setting is off
        val cards = if (showNsfw) skipped else skipped.filterNot { it.anime.nsfw }
        val needle = query.trim()
        // Any of the titles MAL has: romaji, English or Japanese
        val shown = if (needle.isEmpty()) cards else cards.filter { card ->
            TitleLanguage.entries.any { card.anime.titleIn(it)?.contains(needle, ignoreCase = true) == true }
        }
        SkippedUiState(loaded = true, cards = shown, total = cards.size, query = query, titles = titles)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkippedUiState())

    private val _messages = Channel<UndoMessage>(Channel.BUFFERED)
    val messages: Flow<UndoMessage> = _messages.receiveAsFlow()

    fun setQuery(text: String) {
        query.value = text
    }

    /** Back in the decks, nothing changes on MAL. */
    fun restore(card: DeckCard) {
        val animeId = card.anime.id
        viewModelScope.launch {
            val skippedAt = deckRepository.unskip(animeId) ?: return@launch
            telemetry.logSkippedAction(SkippedAction.RESTORE)
            _messages.send(
                UndoMessage(animeId, uiText(R.string.skipped_restored, titleOf(card))) {
                    viewModelScope.launch { deckRepository.skip(animeId, skippedAt) }
                }
            )
        }
    }

    /** Sets the anime on MAL ([episodes] for watching): it leaves the skipped ones (StatusEditor). */
    fun setStatus(card: DeckCard, status: ListStatus, episodes: Int = 0) {
        viewModelScope.launch {
            val undo = statusEditor.setStatus(card.anime, status, episodes)
            telemetry.logSkippedAction(SkippedAction.SET_STATUS)
            _messages.send(
                UndoMessage(card.anime.id, uiText(R.string.skipped_status_set, titleOf(card), uiText(listStatusRes(status)))) {
                    viewModelScope.launch { undo() }
                }
            )
        }
    }

    fun restoreAll() {
        telemetry.logSkippedAction(SkippedAction.RESTORE_ALL)
        viewModelScope.launch { deckRepository.restoreSkipped() }
    }

    private fun titleOf(card: DeckCard): String = card.anime.displayTitle(uiState.value.titles)
}
