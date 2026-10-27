package it.emanuelemelini.mitayo.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.CatalogRepository
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.DeckFilters
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.OtherLists
import it.emanuelemelini.mitayo.data.StatusEditor
import it.emanuelemelini.mitayo.data.SwipeHistory
import it.emanuelemelini.mitayo.data.SyncManager
import it.emanuelemelini.mitayo.data.SyncState
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListEntry
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.db.SwipeAction
import it.emanuelemelini.mitayo.data.mal.MalListStatus
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.telemetry.SwipeOutcome
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.ui.UiText
import it.emanuelemelini.mitayo.ui.toUiText
import it.emanuelemelini.mitayo.ui.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** An undone card comes back from where it left. */
data class Returning(val animeId: Int, val direction: SwipeDirection)

/**
 * The card swiped up (or right while still airing) waits for the episodes watched before it
 * goes to MAL as watching.
 */
data class EpisodesPrompt(val card: DeckCard, val direction: SwipeDirection) {
    /** Swiped right but still airing: it can't be completed. */
    val airing: Boolean get() = direction == SwipeDirection.RIGHT
}

data class DeckUiState(
    val loaded: Boolean = false,
    /** Top card first. */
    val cards: List<DeckCard> = emptyList(),
    val remaining: Int = 0,
    val genreName: String? = null,
    val titles: TitleChoice = TitleChoice(),
    /** Downloading more of the catalog, or the remote list. */
    val loadingMore: Boolean = false,
    /** First catalog download still running: an empty deck isn't finished yet. */
    val catalogSyncing: Boolean = false,
    val canUndo: Boolean = false,
    val returning: Returning? = null,
    val episodesPrompt: EpisodesPrompt? = null,
    /** The deck couldn't load and is empty. */
    val error: UiText? = null,
    /** Another user's deck: their status and score of each anime, shown on the card. */
    val others: Map<Int, MalListStatus> = emptyMap(),
    /** A deck by status: cards passed in this visit. */
    val passed: Int = 0,
    val filters: DeckFilters = DeckFilters(),
    /** Every deck in a random order (a setting for all of them). */
    val shuffled: Boolean = false,
    /** The watching deck: the last episode reached, completed if the user wants. */
    val finishPrompt: DeckCard? = null,
    /** The first deck ever opened: what each swipe does. */
    val showGuide: Boolean = false,
)

private sealed interface UndoAction {
    val card: DeckCard
    val direction: SwipeDirection

    /** Its record in the swipe history, dropped by the undo; null: not recorded. */
    val historyId: Long? get() = null

    data class Skipped(override val card: DeckCard, override val historyId: Long?) : UndoAction {
        override val direction = SwipeDirection.LEFT
    }

    /** The watching deck: one episode more, the card stayed where it was. */
    data class EpisodeAdded(override val card: DeckCard, val previous: ListEntry?, override val historyId: Long?) : UndoAction {
        override val direction = SwipeDirection.UP
    }

    /** A deck by status: swiped left, it only moved on to the next card. */
    data class Passed(override val card: DeckCard) : UndoAction {
        override val direction = SwipeDirection.LEFT
    }

    /** Completed or watching: undo puts back [previous] (null: it wasn't in the list). */
    data class ListChanged(
        override val card: DeckCard,
        override val direction: SwipeDirection,
        val previous: ListEntry?,
        override val historyId: Long?,
    ) : UndoAction
}

/** What the cards query depends on: it runs again when one of them changes. */
private data class CardsQuery(val showNsfw: Boolean, val limit: Int, val filters: DeckFilters, val shuffleSeed: Long?)

/** State that only lives in this deck: swiped cards not yet gone from the query, undo, loading. */
private data class LocalState(
    val gone: Set<Int> = emptySet(),
    val genreName: String? = null,
    val loadingMore: Boolean = false,
    val canUndo: Boolean = false,
    val returning: Returning? = null,
    val episodesPrompt: EpisodesPrompt? = null,
    val error: UiText? = null,
    val others: Map<Int, MalListStatus> = emptyMap(),
    /** A deck by status: swiped left in this visit, back when the deck is opened again. */
    val passed: Set<Int> = emptySet(),
    val filters: DeckFilters = DeckFilters(),
    val finishPrompt: DeckCard? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DeckViewModel(
    private val category: Category,
    private val deckRepository: DeckRepository,
    private val listRepository: ListRepository,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val syncManager: SyncManager,
    private val telemetry: Telemetry,
    private val statusEditor: StatusEditor,
    private val otherLists: OtherLists,
    private val history: SwipeHistory,
) : ViewModel() {

    // A remote deck starts loading: without this the first frame would say "finished"
    private val local = MutableStateFlow(LocalState(loadingMore = category.isRemote))
    private val undoStack = ArrayDeque<UndoAction>()
    private var catalogExhausted = false
    /** After a failure (e.g. offline) the next swipes don't retry and repeat the error at once. */
    private var topUpFailedAt = 0L
    /** A new random order at every visit, when the decks are shuffled. */
    private val shuffleSeed = Random.nextLong(1, SHUFFLE_SEED_LIMIT)
    /** Quick +1 taps: each one reads the episodes the previous one wrote. */
    private val episodeMutex = Mutex()

    /** The details sheet: also opened from a comparison, so it lives in its own class. */
    private val detailsController = AnimeDetailsController(viewModelScope, catalogRepository, listRepository, statusEditor, telemetry)
    val details: StateFlow<DetailsState?> = detailsController.details
    val relatedPrompt: StateFlow<DeckCard?> = detailsController.relatedPrompt

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    /** One-off messages for the snackbar. */
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private val showNsfw = settingsRepository.settings.map { it.showNsfw }.distinctUntilChanged()

    private val cardsQuery = combine(
        showNsfw,
        // The passed cards stay in the query: the window grows with them
        local.map { it.passed.size to it.filters }.distinctUntilChanged(),
        settingsRepository.settings.map { it.shuffleDecks }.distinctUntilChanged(),
    ) { nsfw, (passed, filters), shuffle -> CardsQuery(nsfw, DECK_WINDOW + passed, filters, shuffleSeed.takeIf { shuffle }) }
        .distinctUntilChanged()

    private val deckSettings = settingsRepository.settings
        .map { Triple(it.titles, it.shuffleDecks, !it.deckGuideSeen) }
        .distinctUntilChanged()

    val uiState: StateFlow<DeckUiState> = combine(
        cardsQuery.flatMapLatest { deckRepository.observeCards(category, it.showNsfw, it.limit, it.filters, it.shuffleSeed) },
        cardsQuery.map { it.showNsfw to it.filters }.distinctUntilChanged()
            .flatMapLatest { (nsfw, filters) -> deckRepository.observeRemaining(category, nsfw, filters) },
        deckSettings,
        syncManager.state.map { it is SyncState.Catalog }.distinctUntilChanged(),
        local,
    ) { cards, remaining, (titles, shuffled, showGuide), catalogSyncing, local ->
        DeckUiState(
            loaded = true,
            cards = cards.filter { it.anime.id !in local.gone && it.anime.id !in local.passed },
            remaining = (remaining - cards.count { it.anime.id in local.passed }).coerceAtLeast(0),
            genreName = local.genreName,
            titles = titles,
            loadingMore = local.loadingMore,
            catalogSyncing = catalogSyncing,
            canUndo = local.canUndo,
            returning = local.returning,
            episodesPrompt = local.episodesPrompt,
            error = local.error,
            others = local.others,
            passed = local.passed.size,
            filters = local.filters,
            shuffled = shuffled,
            finishPrompt = local.finishPrompt,
            showGuide = showGuide,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckUiState())

    init {
        if (category is Category.OfGenre) {
            viewModelScope.launch { local.update { it.copy(genreName = deckRepository.genreName(category.genreId)) } }
        }
        if (category.isRemote) refreshRemoteIfStale()
    }

    /** [score]: right with the ★ button, completed with that score. */
    fun swipe(card: DeckCard, direction: SwipeDirection, score: Int? = null) {
        val animeId = card.anime.id
        // A deck by status: left only moves on, so the anime isn't lost to the next visits
        if (direction == SwipeDirection.LEFT && category is Category.OfStatus) {
            local.update { it.copy(passed = it.passed + animeId, returning = null) }
            telemetry.logSwipe(category, SwipeOutcome.PASSED)
            pushUndo(UndoAction.Passed(card))
            return
        }
        // Only announced: MAL takes nothing but plan to watch
        if (card.anime.notYetAired) {
            setStatus(card, ListStatus.PLAN_TO_WATCH, direction)
            return
        }
        // Up, or right on an anime still airing (MAL can't have it completed): the episodes first
        if (direction == SwipeDirection.UP || (direction == SwipeDirection.RIGHT && card.anime.isAiring)) {
            local.update {
                it.copy(gone = it.gone + animeId, returning = null, episodesPrompt = EpisodesPrompt(card, direction))
            }
            return
        }
        local.update { it.copy(gone = it.gone + animeId, returning = null) }
        viewModelScope.launch {
            val action = if (direction == SwipeDirection.LEFT) {
                deckRepository.skip(animeId)
                telemetry.logSwipe(category, SwipeOutcome.SKIPPED)
                UndoAction.Skipped(card, history.record(animeId, SwipeAction.SKIPPED, episodes = 0, score = 0, previous = null))
            } else {
                telemetry.logSwipe(category, SwipeOutcome.COMPLETED)
                if (score != null) telemetry.logScore(category)
                val previous = listRepository.markCompleted(card.anime, score)
                val recordId = history.record(animeId, SwipeAction.COMPLETED, card.anime.episodes, score ?: previous?.score ?: 0, previous)
                UndoAction.ListChanged(card, direction, previous, recordId)
            }
            pushUndo(action)
        }
    }

    /**
     * The ⋯ menu: one of the statuses no swipe gives (on hold, dropped, plan to watch), or plan
     * to watch for an anime only announced. [direction]: where the card flew.
     */
    fun setStatus(card: DeckCard, status: ListStatus, direction: SwipeDirection = SwipeDirection.UP) {
        local.update { it.copy(gone = it.gone + card.anime.id, returning = null) }
        viewModelScope.launch {
            val previous = listRepository.markStatus(card.anime.id, status)
            SwipeOutcome.entries.find { it.name == status.name }?.let { telemetry.logSwipe(category, it) }
            val recordId = history.record(
                card.anime.id,
                SwipeAction.valueOf(status.name),
                previous?.episodesWatched ?: 0,
                previous?.score ?: 0,
                previous,
            )
            // Undo brings it back from where it flew
            pushUndo(UndoAction.ListChanged(card, direction, previous, recordId))
        }
    }

    /** The anime waiting for its episodes goes to MAL as watching with [episodes]. */
    fun confirmEpisodes(episodes: Int) {
        val prompt = local.value.episodesPrompt ?: return
        local.update { it.copy(episodesPrompt = null) }
        viewModelScope.launch {
            val animeId = prompt.card.anime.id
            val previous = listRepository.markWatching(animeId, episodes)
            telemetry.logSwipe(category, SwipeOutcome.WATCHING)
            val recordId = history.record(animeId, SwipeAction.WATCHING, episodes, previous?.score ?: 0, previous)
            pushUndo(UndoAction.ListChanged(prompt.card, prompt.direction, previous, recordId))
        }
    }

    /**
     * The watching deck: one more episode, the card stays where it is. The last episode of an
     * anime that finished airing asks whether it's completed instead ([finishPrompt]).
     */
    fun addEpisode(card: DeckCard) {
        viewModelScope.launch {
            episodeMutex.withLock {
                val animeId = card.anime.id
                val current = listRepository.entry(animeId) ?: return@withLock
                val total = card.anime.episodes
                val next = current.episodesWatched + 1
                if (total > 0 && next >= total && !card.anime.isAiring) {
                    local.update { it.copy(finishPrompt = card) }
                    return@withLock
                }
                // MAL doesn't take more episodes than the anime has
                if (total in 1 until next) return@withLock
                saveEpisodes(card, next)
            }
        }
    }

    /** The last episode, but not completed: watching with every episode. */
    fun keepWatching() {
        val card = local.value.finishPrompt ?: return
        local.update { it.copy(finishPrompt = null) }
        viewModelScope.launch { episodeMutex.withLock { saveEpisodes(card, card.anime.episodes) } }
    }

    /** Completed after all: the screen flings the card right, which completes it. */
    fun closeFinishPrompt() {
        local.update { it.copy(finishPrompt = null) }
    }

    private suspend fun saveEpisodes(card: DeckCard, episodes: Int) {
        val previous = listRepository.markWatching(card.anime.id, episodes)
        telemetry.logSwipe(category, SwipeOutcome.EPISODE)
        val recordId = history.record(card.anime.id, SwipeAction.EPISODE, episodes, previous?.score ?: 0, previous)
        pushUndo(UndoAction.EpisodeAdded(card, previous, recordId))
    }

    fun setFilters(filters: DeckFilters) {
        local.update { it.copy(filters = filters) }
    }

    /** The filters sheet closed: counted only when it leaves some filter on. */
    fun onFiltersClosed() {
        if (local.value.filters.active) telemetry.logDeckFilters(category)
    }

    /** For every deck: the setting is one. */
    fun toggleShuffle() {
        viewModelScope.launch {
            val shuffle = !settingsRepository.currentSettings().shuffleDecks
            settingsRepository.setShuffleDecks(shuffle)
            telemetry.logShuffle(shuffle)
            _messages.send(uiText(if (shuffle) R.string.deck_shuffled else R.string.deck_unshuffled))
        }
    }

    fun onGuideSeen() {
        viewModelScope.launch { settingsRepository.setDeckGuideSeen() }
    }

    /** No episodes given: nothing changes and the card comes back to the top. */
    fun cancelEpisodes() {
        val prompt = local.value.episodesPrompt ?: return
        val animeId = prompt.card.anime.id
        local.update {
            it.copy(gone = it.gone - animeId, returning = Returning(animeId, prompt.direction), episodesPrompt = null)
        }
    }

    private fun pushUndo(action: UndoAction) {
        undoStack.addLast(action)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        local.update { it.copy(canUndo = true) }
    }

    fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        telemetry.logUndo(category)
        viewModelScope.launch {
            val animeId = action.card.anime.id
            when (action) {
                is UndoAction.Skipped -> deckRepository.unskip(animeId)
                is UndoAction.ListChanged -> listRepository.restore(animeId, action.previous)
                is UndoAction.EpisodeAdded -> listRepository.restore(animeId, action.previous)
                is UndoAction.Passed -> Unit
            }
            action.historyId?.let { history.forget(it) }
            local.update {
                it.copy(
                    gone = it.gone - animeId,
                    passed = it.passed - animeId,
                    // A +1 left the card where it was: nothing flies back
                    returning = if (action is UndoAction.EpisodeAdded) it.returning else Returning(animeId, action.direction),
                    canUndo = undoStack.isNotEmpty(),
                )
            }
        }
    }

    /** A deck by status gone through: the passed cards come back, from the first. */
    fun startOver() {
        undoStack.removeAll { it is UndoAction.Passed }
        local.update { it.copy(passed = emptySet(), canUndo = undoStack.isNotEmpty()) }
    }

    fun onReturnShown() {
        local.update { it.copy(returning = null) }
    }

    /** Called when few cards are left: more of the catalog, a few pages at most per call. */
    fun topUp() {
        // More of the catalog doesn't add anime to the user's list: a deck by status has them all
        if (category.isRemote || category is Category.OfStatus || catalogExhausted || local.value.loadingMore) return
        // The running download brings these pages anyway
        if (syncManager.state.value is SyncState.Catalog) return
        if (System.currentTimeMillis() - topUpFailedAt < TOP_UP_RETRY_MS) return
        viewModelScope.launch {
            local.update { it.copy(loadingMore = true) }
            try {
                var pages = 0
                while (pages < MAX_PAGES_PER_TOP_UP && remainingNow() < LOW_WATER) {
                    if (!catalogRepository.loadMore()) {
                        catalogExhausted = true
                        break
                    }
                    pages++
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                topUpFailedAt = System.currentTimeMillis()
                _messages.send(e.toUiText())
            } finally {
                local.update { it.copy(loadingMore = false) }
            }
        }
    }

    fun retry() {
        local.update { it.copy(error = null) }
        topUpFailedAt = 0L
        if (category.isRemote) refreshRemote() else topUp()
    }

    fun openDetails(card: DeckCard) = detailsController.open(card)

    fun closeDetails() = detailsController.close()

    fun setRelatedStatus(related: RelatedAnime, status: ListStatus) = detailsController.setRelatedStatus(related, status)

    fun confirmRelatedEpisodes(episodes: Int) = detailsController.confirmRelatedEpisodes(episodes)

    fun cancelRelatedEpisodes() = detailsController.cancelRelatedEpisodes()

    private suspend fun remainingNow(): Int =
        deckRepository.observeRemaining(category, showNsfw.first()).first()

    private fun refreshRemoteIfStale() {
        viewModelScope.launch {
            if (catalogRepository.remoteListIsStale(category)) {
                refreshRemote()
            } else {
                local.update { it.copy(loadingMore = false) }
            }
        }
    }

    private fun refreshRemote() {
        viewModelScope.launch {
            local.update { it.copy(loadingMore = true) }
            try {
                if (category is Category.OfUser) {
                    // Downloaded at every opening; their statuses stay in memory, for the cards
                    val entries = otherLists.list(category.userName)
                    catalogRepository.saveUserList(category, entries)
                    local.update { state -> state.copy(others = entries.associate { it.node.id to it.listStatus }) }
                } else {
                    catalogRepository.refreshRemoteList(category)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // With a cached list the deck still works: just say it's not fresh
                if (remainingNow() == 0) local.update { it.copy(error = e.toUiText()) } else _messages.send(e.toUiText())
            } finally {
                local.update { it.copy(loadingMore = false) }
            }
        }
    }

    companion object {
        /** Cards loaded at a time; the query moves on by itself after every swipe. */
        private const val DECK_WINDOW = 30
        /** Fewer cards than this: download more of the catalog. */
        const val LOW_WATER = 12
        private const val MAX_PAGES_PER_TOP_UP = 4
        private const val MAX_UNDO = 30
        private const val TOP_UP_RETRY_MS = 60_000L
        /** Seeds below this keep the shuffle hash inside 64 bits (DeckQuery). */
        private const val SHUFFLE_SEED_LIMIT = 1_000_000_007L
    }
}
