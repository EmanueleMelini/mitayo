package it.emanuelemelini.mitayo.ui.deck

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.TitleLanguage
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalVideo
import it.emanuelemelini.mitayo.data.telemetry.ShareKind
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.SkippedLabel
import it.emanuelemelini.mitayo.ui.components.StatusMenu
import it.emanuelemelini.mitayo.ui.components.listStatusLine
import it.emanuelemelini.mitayo.ui.components.metaLine
import it.emanuelemelini.mitayo.ui.components.scoreLabel
import it.emanuelemelini.mitayo.ui.components.shimmer
import it.emanuelemelini.mitayo.ui.malAnimeUri
import it.emanuelemelini.mitayo.ui.openInBrowser
import it.emanuelemelini.mitayo.ui.openVideo
import it.emanuelemelini.mitayo.ui.shareText
import it.emanuelemelini.mitayo.ui.theme.MitayoColors
import it.emanuelemelini.mitayo.ui.youTubeUri

/** Synopsis and details, loaded from MAL when the card is tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimeDetailsSheet(
    state: DetailsState,
    titles: TitleChoice,
    onDismiss: () -> Unit,
    onSetRelatedStatus: (RelatedAnime, ListStatus) -> Unit,
) {
    val context = LocalContext.current
    val anime = state.card.anime
    val details = state.details
    // As in PhotoCal: the sheet stops below the status bar. With the default insets its top padding
    // depends on its offset, and content about as tall as the screen (a long synopsis, the related
    // anime) keeps it bouncing after a fling, until it closes by itself.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row {
                AsyncImage(
                    model = anime.pictureUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(96.dp).height(136.dp).clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val shown = anime.displayTitle(titles)
                    Text(shown, style = MaterialTheme.typography.titleLarge)
                    // Every other title MAL has: the chosen subtitle first, then the rest
                    val otherTitles = (listOfNotNull(titles.subtitle) + TitleLanguage.entries)
                        .mapNotNull { anime.titleIn(it) }
                        .plus(listOfNotNull(details?.alternativeTitles?.ja?.takeIf { it.isNotBlank() }))
                        .distinct()
                        .filter { it != shown }
                    otherTitles.forEach {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(metaLine(anime), style = MaterialTheme.typography.bodyMedium)
                    anime.mean?.let { mean ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Star, contentDescription = null, tint = MitayoColors.Kin, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(scoreLabel(mean), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            when {
                state.error != null -> Text(
                    text = state.error.asString(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Placeholder lines of the synopsis while it loads
                details == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1f, 0.92f, 0.96f, 0.6f).forEach { fraction ->
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .shimmer()
                        )
                    }
                }
                else -> {
                    details.synopsis?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    DetailRow(R.string.details_studios, details.studios.joinToString(", ") { it.name })
                    DetailRow(R.string.details_source, details.source?.prettyApiValue())
                    DetailRow(
                        R.string.details_duration,
                        details.averageEpisodeDuration?.takeIf { it > 0 }?.let { stringResource(R.string.details_minutes, it / 60) },
                    )
                    DetailRow(R.string.details_rating, details.rating?.uppercase()?.replace('_', '-'))
                    val videos = details.videos.mapNotNull { video -> youTubeUri(video.url)?.let { video to it } }
                    if (videos.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.details_videos),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(videos, key = { it.second.toString() }) { (video, uri) ->
                                VideoThumbnail(video, onClick = { openVideo(context, uri) })
                            }
                        }
                    }
                    if (state.related.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.details_related),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        state.relatedError?.let {
                            Text(it.asString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        state.related.forEach { related ->
                            RelatedRow(
                                related = related,
                                busy = state.busyRelated == related.id,
                                onSetStatus = { onSetRelatedStatus(related, it) },
                            )
                        }
                    }
                }
            }
            val telemetry = appContainer().telemetry
            val shareTitle = stringResource(R.string.share_title)
            val shareText = stringResource(R.string.share_anime_text, anime.displayTitle(titles), malAnimeUri(anime.id).toString())
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { openInBrowser(context, malAnimeUri(anime.id)) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.details_open_mal))
                }
                OutlinedButton(onClick = {
                    telemetry.logShare(ShareKind.ANIME)
                    shareText(context, shareText, shareTitle)
                }) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_share))
                }
            }
        }
    }
}

/** A trailer or promotional video: its YouTube preview, opened in YouTube. */
@Composable
private fun VideoThumbnail(video: MalVideo, onClick: () -> Unit) {
    Column(Modifier.width(176.dp).clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(99.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(
                model = video.thumbnail?.takeIf { it.startsWith("https://") },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = stringResource(R.string.details_play_video),
                tint = Color.White,
                modifier = Modifier.size(40.dp),
            )
        }
        video.title?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A sequel, prequel...: relation, title, status in the list (or skipped) and the menu to set it. */
@Composable
private fun RelatedRow(related: RelatedAnime, busy: Boolean, onSetStatus: (ListStatus) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = related.pictureUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(44.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(relationRes(related.relation)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(related.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            related.entry?.let { entry ->
                Text(
                    text = listStatusLine(entry.status, entry.episodesWatched, totalEpisodes = 0, score = entry.score),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (related.skipped) SkippedLabel(style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.width(8.dp))
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            // Its airing status is known only once loaded: a completed one still airing becomes watching
            StatusMenu(related.entry?.status, canComplete = true, onSetStatus = onSetStatus)
        }
    }
}

private fun relationRes(relation: String): Int = when (relation) {
    "prequel" -> R.string.relation_prequel
    "sequel" -> R.string.relation_sequel
    "parent_story" -> R.string.relation_parent_story
    "full_story" -> R.string.relation_full_story
    "side_story" -> R.string.relation_side_story
    "alternative_version" -> R.string.relation_alternative_version
    "alternative_setting" -> R.string.relation_alternative_setting
    "spin_off" -> R.string.relation_spin_off
    "summary" -> R.string.relation_summary
    "character" -> R.string.relation_character
    else -> R.string.relation_other
}

@Composable
private fun DetailRow(labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** "light_novel" → "Light novel". */
private fun String.prettyApiValue(): String = replace('_', ' ').replaceFirstChar { it.uppercase() }

/** The details sheet of [controller], with the episodes of a related anime set as watching. */
@Composable
fun AnimeDetailsHost(controller: AnimeDetailsController, titles: TitleChoice) {
    val details by controller.details.collectAsStateWithLifecycle()
    val relatedPrompt by controller.relatedPrompt.collectAsStateWithLifecycle()
    details?.let {
        AnimeDetailsSheet(state = it, titles = titles, onDismiss = controller::close, onSetRelatedStatus = controller::setRelatedStatus)
    }
    relatedPrompt?.let { card ->
        EpisodesDialog(
            prompt = EpisodesPrompt(card, SwipeDirection.UP),
            titles = titles,
            onConfirm = controller::confirmRelatedEpisodes,
            onDismiss = controller::cancelRelatedEpisodes,
        )
    }
}
