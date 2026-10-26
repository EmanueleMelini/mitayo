package it.emanuelemelini.mitayo.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.ui.components.scoreNameRes

/**
 * The ★ button: completed with a MAL score. Only when asked, so swiping right never stops for
 * a score.
 */
@Composable
fun ScoreDialog(card: DeckCard, titles: TitleChoice, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var score by rememberSaveable(card.anime.id) { mutableStateOf(card.listScore?.takeIf { it > 0 }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.score_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.anime.displayTitle(titles), style = MaterialTheme.typography.titleSmall)
                listOf(1..5, 6..10).forEach { scores ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        scores.forEach { value ->
                            val onClick = { score = value }
                            val buttonModifier = Modifier.weight(1f)
                            val padding = PaddingValues(0.dp)
                            if (value == score) {
                                Button(onClick = onClick, contentPadding = padding, modifier = buttonModifier) { Text("$value") }
                            } else {
                                FilledTonalButton(onClick = onClick, contentPadding = padding, modifier = buttonModifier) { Text("$value") }
                            }
                        }
                    }
                }
                Text(
                    text = score?.let { stringResource(R.string.score_selected, it, stringResource(scoreNameRes(it))) }
                        ?: stringResource(R.string.score_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { score?.let(onConfirm) }, enabled = score != null) {
                Text(stringResource(R.string.score_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
