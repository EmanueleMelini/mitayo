package it.emanuelemelini.mitayo.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import it.emanuelemelini.mitayo.AppLanguage
import it.emanuelemelini.mitayo.AppLocale
import it.emanuelemelini.mitayo.BuildConfig
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.SyncState
import it.emanuelemelini.mitayo.data.TitleLanguage
import it.emanuelemelini.mitayo.data.prefs.ThemeMode
import it.emanuelemelini.mitayo.data.update.ChangelogEntry
import it.emanuelemelini.mitayo.data.update.UpdateState
import it.emanuelemelini.mitayo.ui.AppLinks
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.openInBrowser
import it.emanuelemelini.mitayo.ui.toUiText
import it.emanuelemelini.mitayo.ui.update.ChangelogDialog
import java.text.NumberFormat
import java.time.LocalDate
import kotlinx.coroutines.launch

/** One page of the Profile, opened from its menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePageScreen(page: ProfilePage, onBack: () -> Unit, onOpenSkipped: () -> Unit) {
    val viewModel = profileViewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(page.titleRes)) },
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
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                // Rows that appear or change height (a sync error, a check result) grow smoothly
                .animateContentSize(),
        ) {
            when (page) {
                ProfilePage.APPEARANCE -> AppearancePage(viewModel)
                ProfilePage.CONTENT -> ContentPage(viewModel)
                ProfilePage.NOTIFICATIONS -> NotificationsPage(viewModel)
                ProfilePage.PRIVACY -> PrivacyPage(viewModel)
                ProfilePage.DATA -> DataPage(viewModel, onOpenSkipped)
                ProfilePage.ABOUT -> AboutPage()
            }
        }
    }
}

@Composable
private fun AppearancePage(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings = state.settings
    PageCard {
        // null = the device language; a change recreates the activity in the new language
        ChoiceRow(
            title = stringResource(R.string.profile_language),
            body = null,
            options = listOf<Pair<AppLanguage?, Int>>(
                null to R.string.language_system,
                AppLanguage.ITALIAN to R.string.language_italian,
                AppLanguage.ENGLISH to R.string.language_english,
            ),
            selected = remember { AppLocale.chosen() },
            onSelect = { language -> if (language != AppLocale.chosen()) AppLocale.choose(language) },
        )
        HorizontalDivider()
        ChoiceRow(
            title = stringResource(R.string.profile_theme),
            body = null,
            options = ThemeMode.entries.map { it to themeLabelRes(it) },
            selected = settings.themeMode,
            onSelect = viewModel::setThemeMode,
        )
        HorizontalDivider()
        val languages = TitleLanguage.entries.map { it to titleLabelRes(it) }
        ChoiceRow(
            title = stringResource(R.string.profile_title_language),
            body = stringResource(R.string.profile_title_language_body),
            options = languages,
            selected = settings.titles.title,
            onSelect = viewModel::setTitleLanguage,
        )
        HorizontalDivider()
        ChoiceRow(
            title = stringResource(R.string.profile_subtitle_language),
            body = stringResource(R.string.profile_subtitle_language_body),
            options = listOf<Pair<TitleLanguage?, Int>>(null to R.string.subtitle_none) + languages,
            selected = settings.titles.subtitle,
            onSelect = viewModel::setSubtitleLanguage,
        )
    }
}

@Composable
private fun ContentPage(viewModel: ProfileViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PageCard {
        SwitchRow(
            title = stringResource(R.string.profile_nsfw),
            body = stringResource(R.string.profile_nsfw_body),
            checked = state.settings.showNsfw,
            onCheckedChange = viewModel::setShowNsfw,
        )
    }
}

@Composable
private fun DataPage(viewModel: ProfileViewModel, onOpenSkipped: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val syncing = state.sync is SyncState.UserList || state.sync is SyncState.Catalog
    PageCard {
        ListItem(
            leadingContent = { Icon(Icons.Rounded.Sync, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.profile_sync_now)) },
            supportingContent = {
                val failed = state.sync as? SyncState.Failed
                Text(
                    text = when {
                        failed != null -> failed.error.toUiText().asString()
                        state.syncInfo.listSyncedAt > 0 -> stringResource(R.string.profile_last_sync, formatTime(state.syncInfo.listSyncedAt))
                        else -> stringResource(R.string.profile_never_synced)
                    },
                    color = if (failed != null) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            },
            trailingContent = { if (syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickableRow(enabled = !syncing, onClick = viewModel::syncNow),
        )
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.profile_catalog)) },
            supportingContent = {
                Text(
                    pluralStringResource(
                        R.plurals.profile_catalog_size,
                        state.syncInfo.catalogNextOffset,
                        NumberFormat.getIntegerInstance().format(state.syncInfo.catalogNextOffset),
                    )
                )
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        HorizontalDivider()
        ListItem(
            leadingContent = { Icon(Icons.Rounded.VisibilityOff, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.skipped_title)) },
            supportingContent = {
                Text(pluralStringResource(R.plurals.profile_skipped_count, state.stats.skipped, state.stats.skipped))
            },
            trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickableRow(onClick = onOpenSkipped),
        )
    }
    SkippedBackupCard(viewModel, skippedCount = state.stats.skipped)
}

/** The skipped anime live only on this phone: a file keeps them across a logout or a new phone. */
@Composable
private fun SkippedBackupCard(viewModel: ProfileViewModel, skippedCount: Int) {
    val fileName = stringResource(R.string.backup_file_name, LocalDate.now().toString())
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportSkipped)
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importSkipped)
    }
    PageCard {
        ListItem(
            leadingContent = { Icon(Icons.Rounded.FileUpload, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.backup_export)) },
            supportingContent = { Text(stringResource(R.string.backup_export_body)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickableRow(enabled = skippedCount > 0) { export.launch(fileName) },
        )
        HorizontalDivider()
        ListItem(
            leadingContent = { Icon(Icons.Rounded.FileDownload, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.backup_import)) },
            supportingContent = { Text(stringResource(R.string.backup_import_body)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            // Some file managers give a JSON file another type
            modifier = Modifier.clickableRow { import.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
    }
}

@Composable
private fun AboutPage() {
    val container = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateState by container.appUpdater.state.collectAsStateWithLifecycle()
    var changelog by remember { mutableStateOf<List<ChangelogEntry>?>(null) }
    val checking = updateState == UpdateState.Checking

    PageCard {
        ListItem(
            leadingContent = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.profile_check_updates)) },
            supportingContent = {
                Text(
                    text = when (updateState) {
                        UpdateState.Checking -> stringResource(R.string.profile_checking_updates)
                        UpdateState.UpToDate -> stringResource(R.string.profile_up_to_date, BuildConfig.VERSION_NAME)
                        UpdateState.CheckFailed -> stringResource(R.string.profile_check_failed)
                        else -> stringResource(R.string.profile_version, BuildConfig.VERSION_NAME)
                    },
                    color = if (updateState == UpdateState.CheckFailed) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            },
            trailingContent = { if (checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickableRow(enabled = !checking, onClick = container.appUpdater::checkNow),
        )
        HorizontalDivider()
        ListItem(
            leadingContent = { Icon(Icons.Rounded.NewReleases, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.changelog_title)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickableRow { scope.launch { changelog = container.changelog.entries() } },
        )
    }
    Spacer(Modifier.height(4.dp))
    PageCard {
        LinkRow(Icons.Rounded.Description, stringResource(R.string.profile_terms), null) {
            openInBrowser(context, AppLinks.terms)
        }
        HorizontalDivider()
        LinkRow(Icons.Rounded.PrivacyTip, stringResource(R.string.profile_privacy), null) {
            openInBrowser(context, AppLinks.privacy)
        }
        HorizontalDivider()
        LinkRow(Icons.Rounded.Flag, stringResource(R.string.profile_report), stringResource(R.string.profile_report_body)) {
            openInBrowser(context, AppLinks.issues)
        }
        HorizontalDivider()
        LinkRow(Icons.Rounded.Public, stringResource(R.string.profile_site), null) {
            openInBrowser(context, AppLinks.site)
        }
        HorizontalDivider()
        LinkRow(Icons.Rounded.Code, stringResource(R.string.profile_source), null) {
            openInBrowser(context, AppLinks.source)
        }
        HorizontalDivider()
        LinkRow(Icons.Rounded.Gavel, stringResource(R.string.profile_license), stringResource(R.string.profile_license_body)) {
            openInBrowser(context, AppLinks.license)
        }
    }

    changelog?.let { entries -> ChangelogDialog(entries, onDismiss = { changelog = null }) }
}

internal fun themeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.profile_theme_system
    ThemeMode.DARK -> R.string.profile_theme_dark
    ThemeMode.LIGHT -> R.string.profile_theme_light
}

internal fun titleLabelRes(language: TitleLanguage): Int = when (language) {
    TitleLanguage.ROMAJI -> R.string.title_romaji
    TitleLanguage.ENGLISH -> R.string.title_english
    TitleLanguage.JAPANESE -> R.string.title_japanese
}
