package it.emanuelemelini.mitayo.ui.compare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.AffinityMethod
import it.emanuelemelini.mitayo.data.CatalogRepository
import it.emanuelemelini.mitayo.data.Comparison
import it.emanuelemelini.mitayo.data.ListComparison
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.MalUserNames
import it.emanuelemelini.mitayo.data.OtherLists
import it.emanuelemelini.mitayo.data.StatusEditor
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.mal.MalUserAnime
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.data.toAnime
import it.emanuelemelini.mitayo.ui.UiText
import it.emanuelemelini.mitayo.ui.deck.AnimeDetailsController
import it.emanuelemelini.mitayo.ui.deck.DetailsState
import it.emanuelemelini.mitayo.ui.deck.RelatedAnime
import it.emanuelemelini.mitayo.ui.toUiText
import it.emanuelemelini.mitayo.ui.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Who's compared: [name] null is the logged-in user. */
data class CompareSide(val name: String?)

data class CompareResult(
    val a: CompareSide,
    val b: CompareSide,
    val comparison: Comparison,
) {
    /** Only one of the two can be the user: their status can be set on the anime of the other list. */
    val meIsA: Boolean get() = a.name == null
    val meIsB: Boolean get() = b.name == null
}

data class CompareForm(
    /** The first side is the user themself. */
    val meFirst: Boolean = true,
    val nameA: String = "",
    val nameB: String = "",
)

data class CompareUiState(
    val form: CompareForm = CompareForm(),
    val recentUsers: List<String> = emptyList(),
    val myName: String? = null,
    val titles: TitleChoice = TitleChoice(),
    /** The setting: which of the two affinities the result shows. */
    val affinityMethod: AffinityMethod = AffinityMethod.SHARED_SCORES,
    val loading: Boolean = false,
    val error: UiText? = null,
    val result: CompareResult? = null,
    /** Statuses the user set from this page, shown at once on the anime of the other list. */
    val setStatuses: Map<Int, ListStatus> = emptyMap(),
)

/** Two MAL lists side by side, read only when asked and kept only in memory. */
class CompareViewModel(
    private val otherLists: OtherLists,
    private val statusEditor: StatusEditor,
    private val settingsRepository: SettingsRepository,
    private val telemetry: Telemetry,
    catalogRepository: CatalogRepository,
    listRepository: ListRepository,
) : ViewModel() {

    private val local = MutableStateFlow(CompareUiState())
    private var job: Job? = null

    /** The same details sheet as the decks, from the poster of any anime of the comparison. */
    private val detailsController = AnimeDetailsController(viewModelScope, catalogRepository, listRepository, statusEditor, telemetry)
    val details: StateFlow<DetailsState?> = detailsController.details
    val relatedPrompt: StateFlow<DeckCard?> = detailsController.relatedPrompt

    fun openDetails(anime: MalAnime) = detailsController.open(DeckCard(anime.toAnime(), null, null, null))

    fun closeDetails() = detailsController.close()

    fun setRelatedStatus(related: RelatedAnime, status: ListStatus) = detailsController.setRelatedStatus(related, status)

    fun confirmRelatedEpisodes(episodes: Int) = detailsController.confirmRelatedEpisodes(episodes)

    fun cancelRelatedEpisodes() = detailsController.cancelRelatedEpisodes()

    val uiState: StateFlow<CompareUiState> = combine(
        local,
        settingsRepository.recentUsers,
        settingsRepository.settings.map { Triple(it.userName, it.titles, it.affinityMethod) },
    ) { state, recent, (myName, titles, affinityMethod) ->
        state.copy(recentUsers = recent, myName = myName, titles = titles, affinityMethod = affinityMethod)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompareUiState())

    fun setMeFirst(meFirst: Boolean) = updateForm { it.copy(meFirst = meFirst) }

    fun setNameA(name: String) = updateForm { it.copy(nameA = name.trim()) }

    fun setNameB(name: String) = updateForm { it.copy(nameB = name.trim()) }

    /** A recent name fills the first empty field. */
    fun useRecent(name: String) = updateForm { form ->
        if (!form.meFirst && form.nameA.isEmpty()) form.copy(nameA = name) else form.copy(nameB = name)
    }

    fun clearRecent() {
        viewModelScope.launch { settingsRepository.clearRecentUsers() }
    }

    fun compare() {
        val form = local.value.form
        val a = CompareSide(if (form.meFirst) null else form.nameA)
        val b = CompareSide(form.nameB)
        val invalid = listOfNotNull(a.name, b.name).firstOrNull { !MalUserNames.isValid(it) }
        if (invalid != null || b.name.isNullOrEmpty()) {
            local.update { it.copy(error = uiText(R.string.compare_invalid_name, invalid ?: "")) }
            return
        }
        job?.cancel()
        local.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            try {
                val listA = otherLists.list(a.name)
                val listB = otherLists.list(b.name)
                val comparison = withContext(Dispatchers.Default) { ListComparison.compare(listA, listB) }
                listOfNotNull(a.name, b.name).reversed().forEach { settingsRepository.addRecentUser(it) }
                telemetry.logCompare(withMe = a.name == null)
                local.update { it.copy(loading = false, result = CompareResult(a, b, comparison), setStatuses = emptyMap()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(loading = false, error = e.toUiText()) }
            }
        }
    }

    /** On an anime of the other list: in the user's own list, like from the search. */
    fun setStatus(entry: MalUserAnime, status: ListStatus, episodes: Int = 0) {
        viewModelScope.launch {
            statusEditor.setStatus(entry.node.toAnime(), status, episodes)
            telemetry.logCompareSetStatus()
            local.update { it.copy(setStatuses = it.setStatuses + (entry.node.id to status)) }
        }
    }

    private fun updateForm(change: (CompareForm) -> CompareForm) {
        local.update { it.copy(form = change(it.form), error = null) }
    }
}
