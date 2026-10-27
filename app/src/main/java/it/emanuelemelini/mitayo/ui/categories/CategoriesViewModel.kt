package it.emanuelemelini.mitayo.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.GenreGroup
import it.emanuelemelini.mitayo.data.GenreGroups
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.StatusEditor
import it.emanuelemelini.mitayo.data.SyncManager
import it.emanuelemelini.mitayo.data.SyncState
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.DecadeCount
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.GenreCount
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.data.telemetry.TonightAction
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesUiState(
    val loaded: Boolean = false,
    val userName: String? = null,
    val completedCount: Int = 0,
    val genres: List<GenreCount> = emptyList(),
    val themes: List<GenreCount> = emptyList(),
    val demographics: List<GenreCount> = emptyList(),
    /** Newest first, the older anime last. */
    val decades: List<DecadeCount> = emptyList(),
    /** How many anime of the user's list each status has, for the decks by status. */
    val statuses: Map<ListStatus, Int> = emptyMap(),
    val sync: SyncState = SyncState.Idle,
    val tonight: TonightState? = null,
    val titles: TitleChoice = TitleChoice(),
) {
    val isSyncing: Boolean get() = sync is SyncState.UserList || sync is SyncState.Catalog
}

/** "What do I watch tonight?": [card] null when the plan to watch has nothing to start. */
data class TonightState(val card: DeckCard?, val started: Boolean = false)

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel(
    private val settingsRepository: SettingsRepository,
    private val deckRepository: DeckRepository,
    listRepository: ListRepository,
    private val syncManager: SyncManager,
    private val statusEditor: StatusEditor,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val tonight = MutableStateFlow<TonightState?>(null)
    /** Anime already proposed this time: "another one" doesn't propose them again. */
    private val proposed = mutableListOf<Int>()

    private val showNsfw = settingsRepository.settings.map { it.showNsfw }.distinctUntilChanged()

    private val counts = showNsfw.flatMapLatest { showNsfw ->
        deckRepository.observeGenreCounts(showNsfw).map { counts ->
            counts.filter { showNsfw || it.id !in GenreGroups.adult }.groupBy { GenreGroups.of(it.id) }
        }
    }

    private val decadesAndStatuses = showNsfw.flatMapLatest { showNsfw ->
        combine(deckRepository.observeDecadeCounts(showNsfw), deckRepository.observeStatusCounts(showNsfw)) { decades, statuses ->
            decades to statuses.associate { it.status to it.remaining }
        }
    }

    val uiState: StateFlow<CategoriesUiState> = combine(
        combine(settingsRepository.settings, tonight, ::Pair),
        counts,
        decadesAndStatuses,
        listRepository.completedCount,
        syncManager.state,
    ) { (settings, tonight), groups, (decades, statuses), completed, sync ->
        CategoriesUiState(
            loaded = true,
            userName = settings.userName,
            completedCount = completed,
            genres = groups[GenreGroup.GENRE].orEmpty(),
            themes = groups[GenreGroup.THEME].orEmpty(),
            demographics = groups[GenreGroup.DEMOGRAPHIC].orEmpty(),
            decades = decades,
            statuses = statuses,
            sync = sync,
            tonight = tonight,
            titles = settings.titles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun refresh() = syncManager.syncNow()

    /** A random anime of the plan to watch; after the last one the round starts again. */
    fun pickTonight() {
        viewModelScope.launch {
            val showNsfw = settingsRepository.currentSettings().showNsfw
            val card = deckRepository.randomToStart(showNsfw, proposed)
                ?: deckRepository.randomToStart(showNsfw, emptyList()).also { proposed.clear() }
            card?.let { proposed += it.anime.id }
            if (tonight.value != null) telemetry.logTonight(TonightAction.PICK)
            tonight.value = TonightState(card)
        }
    }

    /** The proposed anime becomes watching, from episode 0. */
    fun startTonight() {
        val card = tonight.value?.card ?: return
        viewModelScope.launch {
            statusEditor.setStatus(card.anime, ListStatus.WATCHING, episodes = 0)
            telemetry.logTonight(TonightAction.START)
            tonight.value = TonightState(card, started = true)
        }
    }

    fun closeTonight() {
        tonight.value = null
        proposed.clear()
    }
}
