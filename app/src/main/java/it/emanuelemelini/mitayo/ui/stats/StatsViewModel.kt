package it.emanuelemelini.mitayo.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.data.ListComparison
import it.emanuelemelini.mitayo.data.ListStats
import it.emanuelemelini.mitayo.data.OtherLists
import it.emanuelemelini.mitayo.ui.UiText
import it.emanuelemelini.mitayo.ui.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StatsUiState(
    val loading: Boolean = true,
    val stats: ListStats? = null,
    /** Genre name and how many anime seen or being seen have it, the most frequent first. */
    val genres: List<Pair<String, Int>> = emptyList(),
    val error: UiText? = null,
)

/**
 * The numbers of the user's own list, read from MAL when the page opens (as a comparison does:
 * the episode lengths aren't in the local copy). They stay only in memory.
 */
class StatsViewModel(private val otherLists: OtherLists) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = otherLists.list(null)
                _uiState.value = StatsUiState(
                    loading = false,
                    stats = ListComparison.stats(list),
                    genres = ListComparison.topGenres(list),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false, error = e.toUiText()) }
            }
        }
    }
}
