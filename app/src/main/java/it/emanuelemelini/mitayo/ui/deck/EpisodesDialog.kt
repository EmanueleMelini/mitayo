package it.emanuelemelini.mitayo.ui.deck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.TitleChoice

/**
 * Episodes watched for an anime swiped up (or right while airing): it opens with the episodes
 * already on MAL, selected, so typing replaces them. Never more than the episodes MAL knows of.
 */
@Composable
fun EpisodesDialog(
    prompt: EpisodesPrompt,
    titles: TitleChoice,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val anime = prompt.card.anime
    val total = anime.episodes
    var field by remember(anime.id) {
        val text = "${prompt.card.listEpisodes ?: 0}"
        mutableStateOf(TextFieldValue(text, selection = TextRange(0, text.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(anime.id) { focusRequester.requestFocus() }

    val value = field.text.toIntOrNull()
    val error = when {
        value == null -> stringResource(R.string.episodes_invalid)
        total > 0 && value > total -> pluralStringResource(R.plurals.episodes_too_many, total, total)
        else -> null
    }
    fun set(episodes: Int) {
        val text = "${episodes.coerceIn(0, if (total > 0) total else MAX_UNKNOWN_EPISODES)}"
        field = TextFieldValue(text, selection = TextRange(text.length))
    }
    fun confirm() {
        if (error == null && value != null) onConfirm(value)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.episodes_title)) },
        text = {
            Column {
                Text(anime.displayTitle(titles), style = MaterialTheme.typography.titleSmall)
                if (prompt.airing) {
                    Text(
                        text = stringResource(R.string.episodes_airing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { set((value ?: 0) - 1) }, enabled = (value ?: 0) > 0) {
                        Icon(Icons.Rounded.Remove, contentDescription = stringResource(R.string.episodes_minus))
                    }
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it.copy(text = it.text.filter(Char::isDigit).take(5)) },
                        singleLine = true,
                        isError = error != null,
                        label = { Text(stringResource(R.string.episodes_label)) },
                        supportingText = {
                            when {
                                error != null -> Text(error)
                                total > 0 -> Text(stringResource(R.string.episodes_of, total))
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    IconButton(onClick = { set((value ?: 0) + 1) }, enabled = total <= 0 || (value ?: 0) < total) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.episodes_plus))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = ::confirm) { Text(stringResource(R.string.episodes_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Upper bound for the episode count when MAL doesn't know it yet. */
private const val MAX_UNKNOWN_EPISODES = 99_999
