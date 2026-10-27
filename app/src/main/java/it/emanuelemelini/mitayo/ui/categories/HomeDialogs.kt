package it.emanuelemelini.mitayo.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.AnimeSeason
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.ui.components.metaLine
import it.emanuelemelini.mitayo.ui.components.seasonName

/**
 * A season of any year, from MAL's seasonal lists: the previous one is chosen at first. The
 * current one opens the usual season deck.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SeasonPickerDialog(onOpen: (Category) -> Unit, onDismiss: () -> Unit) {
    val current = remember { AnimeSeason.current() }
    val previous = remember {
        val index = Category.OfSeason.SEASONS.indexOf(current.season)
        if (index == 0) AnimeSeason(current.year - 1, Category.OfSeason.SEASONS.last())
        else AnimeSeason(current.year, Category.OfSeason.SEASONS[index - 1])
    }
    var year by rememberSaveable { mutableIntStateOf(previous.year) }
    var season by rememberSaveable { mutableStateOf(previous.season) }
    // Next year too: its first seasons are already announced
    val lastYear = current.year + 1
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.season_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.season_picker_year), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    IconButton(onClick = { year-- }, enabled = year > Category.OfSeason.FIRST_YEAR) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = null)
                    }
                    Text("$year", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.width(64.dp))
                    IconButton(onClick = { year++ }, enabled = year < lastYear) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.OfSeason.SEASONS.forEach { name ->
                        FilterChip(selected = season == name, onClick = { season = name }, label = { Text(seasonName(name).orEmpty()) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onOpen(if (year == current.year && season == current.season) Category.Season else Category.OfSeason(year, season))
            }) { Text(stringResource(R.string.season_picker_open)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** "What do I watch tonight?": a random anime of the plan to watch, to start or to change. */
@Composable
fun TonightDialog(state: TonightState, titles: TitleChoice, onAnother: () -> Unit, onStart: () -> Unit, onDismiss: () -> Unit) {
    val card = state.card
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (state.started) R.string.tonight_started_title else R.string.tonight_title)) },
        text = {
            if (card == null) {
                Text(stringResource(R.string.tonight_empty))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = card.anime.pictureUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 84.dp, height = 120.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(card.anime.displayTitle(titles), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(metaLine(card.anime), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.started) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.tonight_started), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (card != null && !state.started) {
                TextButton(onClick = onStart) { Text(stringResource(R.string.tonight_start)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        },
        dismissButton = {
            if (card != null && !state.started) {
                Row {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
                    TextButton(onClick = onAnother) { Text(stringResource(R.string.tonight_another)) }
                }
            }
        },
    )
}
