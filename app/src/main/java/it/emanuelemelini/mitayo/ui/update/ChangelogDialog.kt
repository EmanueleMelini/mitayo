package it.emanuelemelini.mitayo.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.update.ChangelogEntry

/** Release notes, newest first; with several versions each one has its own heading. */
@Composable
fun ChangelogDialog(entries: List<ChangelogEntry>, onDismiss: () -> Unit) {
    val single = entries.singleOrNull()
    val language = LocalConfiguration.current.locales[0].language
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (single != null) stringResource(R.string.changelog_title_version, single.version.toString())
                else stringResource(R.string.changelog_title)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                entries.forEach { entry ->
                    if (single == null) {
                        Text(
                            stringResource(R.string.changelog_version, entry.version.toString()),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    entry.notesFor(language).forEach { NoteRow(it) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) }
        },
    )
}

/** One bullet point of release notes. */
@Composable
internal fun NoteRow(note: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•", style = MaterialTheme.typography.bodyMedium)
        Text(note, style = MaterialTheme.typography.bodyMedium)
    }
}
