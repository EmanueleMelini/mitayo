package it.emanuelemelini.mitayo.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.ui.UiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** What just happened to [animeId], for a snackbar with "Undo". */
class UndoMessage(val animeId: Int, val text: UiText, val undo: () -> Unit)

/**
 * Shows each message with "Undo"; a new one replaces the snackbar of the previous one.
 * [onUndo] runs before the message's own undo (e.g. to scroll to the anime).
 */
@Composable
fun UndoMessagesEffect(messages: Flow<UndoMessage>, snackbarHostState: SnackbarHostState, onUndo: (UndoMessage) -> Unit = {}) {
    val context: Context = LocalContext.current
    val undoLabel = stringResource(R.string.snackbar_undo)
    val currentOnUndo by rememberUpdatedState(onUndo)
    LaunchedEffect(messages) {
        messages.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            launch {
                val result = snackbarHostState.showSnackbar(
                    message = message.text.resolve(context),
                    actionLabel = undoLabel,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    currentOnUndo(message)
                    message.undo()
                }
            }
        }
    }
}

/**
 * An anime in a list (skipped page, search): poster, titles, MAL status and the menu to set a
 * new one. [onRestore]: the "Restore" button of the skipped page; [skipped] marks a skipped anime
 * found by the search.
 */
@Composable
fun AnimeRow(
    card: DeckCard,
    titles: TitleChoice,
    onSetStatus: (ListStatus) -> Unit,
    modifier: Modifier = Modifier,
    onRestore: (() -> Unit)? = null,
    skipped: Boolean = false,
    /** Tapping the poster opens the details, as ⓘ in a deck. */
    onOpenDetails: (() -> Unit)? = null,
) {
    val anime = card.anime
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)) {
            AsyncImage(
                model = anime.pictureUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 60.dp, height = 86.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .then(if (onOpenDetails != null) Modifier.clickable(onClick = onOpenDetails) else Modifier),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = anime.displayTitle(titles),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                anime.subtitle(titles)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = metaLine(anime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                card.listStatus?.let { status ->
                    Text(
                        text = stringResource(R.string.deck_in_your_list, listStatusLine(status, card.listEpisodes, anime.episodes, card.listScore)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (skipped) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.search_skipped),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        ) {
            if (onRestore != null) {
                TextButton(onClick = onRestore) {
                    Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.skipped_restore))
                }
            }
            StatusMenu(card.listStatus, canComplete = !card.anime.isAiring, onSetStatus = onSetStatus)
        }
    }
}

/**
 * "Set status" with the MAL statuses in list order, [current] checked; completed only when
 * [canComplete] (an anime still airing can't be).
 */
@Composable
fun StatusMenu(current: ListStatus?, canComplete: Boolean, onSetStatus: (ListStatus) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val statuses = listOf(
        ListStatus.WATCHING,
        ListStatus.COMPLETED,
        ListStatus.ON_HOLD,
        ListStatus.DROPPED,
        ListStatus.PLAN_TO_WATCH,
    ).filter { it != ListStatus.COMPLETED || canComplete }
    Box {
        FilledTonalButton(onClick = { open = true }) {
            Text(stringResource(R.string.skipped_set_status))
            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = RoundedCornerShape(16.dp)) {
            statuses.forEach { status ->
                DropdownMenuItem(
                    text = { Text(listStatusLabel(status)) },
                    leadingIcon = { Icon(listStatusIcon(status), contentDescription = null) },
                    trailingIcon = { if (status == current) Icon(Icons.Rounded.Check, contentDescription = null) },
                    onClick = {
                        open = false
                        onSetStatus(status)
                    },
                )
            }
        }
    }
}

/** An icon, a title and a line of explanation, centered: empty lists and searches without results. */
@Composable
fun EmptyMessage(icon: ImageVector, title: String, body: String?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        body?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
