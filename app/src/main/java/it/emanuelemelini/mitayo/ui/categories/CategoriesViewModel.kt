package it.emanuelemelini.mitayo.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.data.DeckRepository
import it.emanuelemelini.mitayo.data.GenreGroup
import it.emanuelemelini.mitayo.data.GenreGroups
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.SyncManager
import it.emanuelemelini.mitayo.data.SyncState
import it.emanuelemelini.mitayo.data.db.DecadeCount
import it.emanuelemelini.mitayo.data.db.GenreCount
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
) {
    val isSyncing: Boolean get() = sync is SyncState.UserList || sync is SyncState.Catalog
}

@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel(
    settingsRepository: SettingsRepository,
    deckRepository: DeckRepository,
    listRepository: ListRepository,
    private val syncManager: SyncManager,
) : ViewModel() {

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
        settingsRepository.settings,
        counts,
        decadesAndStatuses,
        listRepository.completedCount,
        syncManager.state,
    ) { settings, groups, (decades, statuses), completed, sync ->
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState())

    fun refresh() = syncManager.syncNow()
}
