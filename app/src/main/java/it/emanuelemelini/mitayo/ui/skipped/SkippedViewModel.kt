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

/** Which skipped anime to show, by their status in the user's list. */
sealed interface StatusFilter {
    data object All : StatusFilter

    /** Not in the list at all: skipped from the normal decks. */
    data object NotInList : StatusFilter

    data class Of(val status: ListStatus) : StatusFilter
}

data class SkippedUiState(
    val loaded: Boolean = false,
    /** The skipped anime that match [query] and [filter], the last skipped first. */
    val cards: List<DeckCard> = emptyList(),
    /** Every skipped anime, whatever the search. */
    val total: Int = 0,
    val query: String = "",
    val filter: StatusFilter = StatusFilter.All,
    /** The filters with at least one anime, and how many; empty when none of them is in the list. */
    val filters: List<Pair<StatusFilter, Int>> = emptyList(),
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
    val details = AnimeDetailsController(viewModelScope, catalogRepository, listRepository, deckRepository, statusEditor, telemetry)

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<StatusFilter>(StatusFilter.All)

    val uiState: StateFlow<SkippedUiState> = combine(
        deckRepository.skipped,
        query,
        filter,
        settingsRepository.settings.map { it.titles to it.showNsfw }.distinctUntilChanged(),
    ) { skipped, query, filter, (titles, showNsfw) ->
        // 18+ anime stay out of sight here too while the setting is off
        val cards = if (showNsfw) skipped else skipped.filterNot { it.anime.nsfw }
        val filters = filtersOf(cards)
        // The last anime with that status went away: back to all of them
        val shownFilter = filter.takeIf { chosen -> filters.any { it.first == chosen } } ?: StatusFilter.All
        val needle = query.trim()
        val shown = cards.filter { card ->
            shownFilter.matches(card) &&
                // Any of the titles MAL has: romaji, English or Japanese
                (needle.isEmpty() || TitleLanguage.entries.any { card.anime.titleIn(it)?.contains(needle, ignoreCase = true) == true })
        }
        SkippedUiState(
            loaded = true,
            cards = shown,
            total = cards.size,
            query = query,
            filter = shownFilter,
            filters = filters,
            titles = titles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SkippedUiState())

    private val _messages = Channel<UndoMessage>(Channel.BUFFERED)
    val messages: Flow<UndoMessage> = _messages.receiveAsFlow()

    fun setQuery(text: String) {
        query.value = text
    }

    fun setFilter(value: StatusFilter) {
        filter.value = value
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

internal fun StatusFilter.matches(card: DeckCard): Boolean = when (this) {
    StatusFilter.All -> true
    StatusFilter.NotInList -> card.listStatus == null
    is StatusFilter.Of -> card.listStatus == status
}

/** No filters when none of the skipped anime is in the list: there would be nothing to choose. */
internal fun filtersOf(cards: List<DeckCard>): List<Pair<StatusFilter, Int>> {
    if (cards.none { it.listStatus != null }) return emptyList()
    val groups = listOf(StatusFilter.All, StatusFilter.NotInList) + ListStatus.entries.map { StatusFilter.Of(it) }
    return groups.map { group -> group to cards.count { group.matches(it) } }.filter { it.second > 0 }
}
