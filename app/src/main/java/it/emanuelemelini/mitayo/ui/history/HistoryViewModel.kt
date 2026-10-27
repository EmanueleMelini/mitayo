package it.emanuelemelini.mitayo.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.SwipeHistory
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.SwipeRecordCard
import it.emanuelemelini.mitayo.data.prefs.SettingsRepository
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.ui.UiText
import it.emanuelemelini.mitayo.ui.uiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HistoryUiState(
    val loaded: Boolean = false,
    /** The last swipe first; 18+ anime only with adult content on. */
    val records: List<SwipeRecordCard> = emptyList(),
    val titles: TitleChoice = TitleChoice(),
)

class HistoryViewModel(
    private val history: SwipeHistory,
    settingsRepository: SettingsRepository,
    private val telemetry: Telemetry,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(history.records, settingsRepository.settings) { records, settings ->
        HistoryUiState(
            loaded = true,
            records = if (settings.showNsfw) records else records.filterNot { it.anime.nsfw },
            titles = settings.titles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    /** The anime goes back to how it was before that swipe. */
    fun undo(card: SwipeRecordCard) {
        viewModelScope.launch {
            history.undo(card.record)
            telemetry.logHistoryUndo()
            _messages.send(uiText(R.string.history_undone, card.anime.displayTitle(uiState.value.titles)))
        }
    }
}
