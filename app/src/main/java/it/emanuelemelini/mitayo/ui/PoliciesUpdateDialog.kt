package it.emanuelemelini.mitayo.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import it.emanuelemelini.mitayo.R

/**
 * Terms of use or privacy policy changed since the user accepted them: Mitayo is usable again
 * only after opening both and accepting them. Declining logs out (which deletes the user's data
 * from the phone).
 */
@Composable
fun PoliciesUpdateDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
    val context = LocalContext.current
    var openedTerms by rememberSaveable { mutableStateOf(false) }
    var openedPrivacy by rememberSaveable { mutableStateOf(false) }
    var openFirst by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        // Not dismissable: one of the two answers is needed
        onDismissRequest = {},
        title = { Text(stringResource(R.string.policies_update_title)) },
        text = {
            Column {
                Text(stringResource(R.string.policies_update_body))
                TextButton(onClick = { if (openInBrowser(context, AppLinks.terms)) openedTerms = true }) {
                    Text(stringResource(R.string.profile_terms))
                }
                TextButton(onClick = { if (openInBrowser(context, AppLinks.privacy)) openedPrivacy = true }) {
                    Text(stringResource(R.string.profile_privacy))
                }
                if (openFirst && !(openedTerms && openedPrivacy)) {
                    Text(
                        text = stringResource(R.string.policies_open_first),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (openedTerms && openedPrivacy) onAccept() else openFirst = true }) {
                Text(stringResource(R.string.policies_update_accept))
            }
        },
        dismissButton = { TextButton(onClick = onDecline) { Text(stringResource(R.string.policies_update_decline)) } },
    )
}
