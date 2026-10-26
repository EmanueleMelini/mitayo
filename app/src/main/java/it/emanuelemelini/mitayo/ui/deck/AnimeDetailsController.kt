package it.emanuelemelini.mitayo.ui.deck

import it.emanuelemelini.mitayo.data.CatalogRepository
import it.emanuelemelini.mitayo.data.ListRepository
import it.emanuelemelini.mitayo.data.StatusEditor
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListEntry
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.telemetry.Telemetry
import it.emanuelemelini.mitayo.ui.UiText
import it.emanuelemelini.mitayo.ui.toUiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailsState(
    val card: DeckCard,
    val details: MalAnime? = null,
    val error: UiText? = null,
    /** Sequels, prequels and the rest of the story, with their status in the user's list. */
    val related: List<RelatedAnime> = emptyList(),
    /** The related anime being loaded to set its status. */
    val busyRelated: Int? = null,
    val relatedError: UiText? = null,
)

/** A related anime as the details list it: MAL gives only id, title and picture. */
data class RelatedAnime(
    val id: Int,
    val title: String,
    val pictureUrl: String?,
    /** MAL's relation_type: sequel, prequel, side_story... */
    val relation: String,
    val entry: ListEntry? = null,
)

/**
 * The details sheet of an anime (AnimeDetailsSheet), wherever it opens from (a deck, a
 * comparison): MAL's details, the related anime with their status in the user's list, and the
 * status to set on them. It lives in the [scope] of the ViewModel that owns it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnimeDetailsController(
    private val scope: CoroutineScope,
    private val catalogRepository: CatalogRepository,
    listRepository: ListRepository,
    private val statusEditor: StatusEditor,
    private val telemetry: Telemetry,
) {
    private val _details = MutableStateFlow<DetailsState?>(null)

    /** The details with the list status of the related anime, which changes from the sheet itself. */
    val details: StateFlow<DetailsState?> = _details.flatMapLatest { state ->
        val ids = state?.related?.map { it.id }.orEmpty()
        if (state == null || ids.isEmpty()) {
            flowOf(state)
        } else {
            listRepository.observeEntries(ids).map { entries ->
                val byId = entries.associateBy { it.animeId }
                state.copy(related = state.related.map { it.copy(entry = byId[it.id]) })
            }
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _relatedPrompt = MutableStateFlow<DeckCard?>(null)
    /** A related anime set as watching (or completed while airing): its episodes first. */
    val relatedPrompt: StateFlow<DeckCard?> = _relatedPrompt.asStateFlow()

    fun open(card: DeckCard) {
        _details.value = DetailsState(card)
        scope.launch {
            val loaded = try {
                val details = catalogRepository.details(card.anime.id)
                val related = details.relatedAnime
                    .distinctBy { it.node.id }
                    .sortedBy { RELATION_ORDER.indexOf(it.relationType).takeIf { index -> index >= 0 } ?: RELATION_ORDER.size }
                    .map { RelatedAnime(it.node.id, it.node.title, it.node.mainPicture?.medium, it.relationType) }
                DetailsState(card, details = details, related = related)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DetailsState(card, error = e.toUiText())
            }
            // Only if the sheet is still open on this anime
            _details.update { current -> if (current?.card?.anime?.id == card.anime.id) loaded else current }
        }
    }

    fun close() {
        _details.value = null
    }

    /**
     * From the details: the related anime goes on MAL with [status]. MAL lists it with only a
     * title, so it's loaded first (from the catalog or MAL) for its episodes and airing status.
     */
    fun setRelatedStatus(related: RelatedAnime, status: ListStatus) {
        scope.launch {
            _details.update { it?.copy(busyRelated = related.id, relatedError = null) }
            try {
                val anime = catalogRepository.anime(related.id)
                // Still airing: watching with its episodes, like a right swipe
                if (status == ListStatus.WATCHING || (status == ListStatus.COMPLETED && anime.isAiring)) {
                    val entry = related.entry
                    _relatedPrompt.value = DeckCard(anime, entry?.status, entry?.episodesWatched, entry?.score)
                } else {
                    statusEditor.setStatus(anime, status)
                    telemetry.logRelatedSetStatus()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _details.update { it?.copy(relatedError = e.toUiText()) }
            } finally {
                _details.update { it?.copy(busyRelated = null) }
            }
        }
    }

    fun confirmRelatedEpisodes(episodes: Int) {
        val card = _relatedPrompt.value ?: return
        _relatedPrompt.value = null
        scope.launch {
            statusEditor.setStatus(card.anime, ListStatus.WATCHING, episodes)
            telemetry.logRelatedSetStatus()
        }
    }

    fun cancelRelatedEpisodes() {
        _relatedPrompt.value = null
    }

    private companion object {
        /** The story in order first, then the rest. */
        val RELATION_ORDER = listOf(
            "prequel", "sequel", "parent_story", "full_story", "side_story", "alternative_version",
            "alternative_setting", "spin_off", "summary", "character", "other",
        )
    }
}
