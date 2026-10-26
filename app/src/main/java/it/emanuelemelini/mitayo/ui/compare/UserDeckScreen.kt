package it.emanuelemelini.mitayo.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.MalUserNames
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import kotlinx.coroutines.launch

/** The user name of the deck made of another user's list: the deck itself is a normal one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDeckScreen(onBack: () -> Unit, onOpenDeck: (Category) -> Unit) {
    val container = appContainer()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val recent by container.settingsRepository.recentUsers.collectAsStateWithLifecycle(initialValue = emptyList())
    var name by rememberSaveable { mutableStateOf("") }
    val valid = MalUserNames.isValid(name)

    fun open(userName: String) {
        if (!MalUserNames.isValid(userName)) return
        focusManager.clearFocus()
        scope.launch { container.settingsRepository.addRecentUser(userName) }
        onOpenDeck(Category.OfUser(userName))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.user_deck_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.user_deck_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.staggeredEntrance(0),
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.trim() },
                placeholder = { Text(stringResource(R.string.compare_name_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                isError = name.isNotEmpty() && !valid,
                shape = RoundedCornerShape(50),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { open(name) }),
                modifier = Modifier.fillMaxWidth().staggeredEntrance(1),
            )
            if (recent.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.staggeredEntrance(2),
                ) {
                    recent.forEach { user -> FilterChip(selected = false, onClick = { open(user) }, label = { Text(user) }) }
                    TextButton(onClick = { scope.launch { container.settingsRepository.clearRecentUsers() } }) {
                        Text(stringResource(R.string.compare_clear_recent))
                    }
                }
            }
            Button(onClick = { open(name) }, enabled = valid, modifier = Modifier.fillMaxWidth().staggeredEntrance(3)) {
                Icon(Icons.Rounded.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.user_deck_open))
            }
            Text(
                text = stringResource(R.string.user_deck_public_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
