package it.emanuelemelini.mitayo.ui.compare

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.AffinityMethod
import it.emanuelemelini.mitayo.data.ListStats
import it.emanuelemelini.mitayo.data.SharedAnime
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.mal.MalUserAnime
import it.emanuelemelini.mitayo.data.telemetry.ShareKind
import it.emanuelemelini.mitayo.data.toAnime
import it.emanuelemelini.mitayo.ui.AppLinks
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.StatusMenu
import it.emanuelemelini.mitayo.ui.components.listStatusLabel
import it.emanuelemelini.mitayo.ui.components.malEntryLine
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import it.emanuelemelini.mitayo.ui.deck.AnimeDetailsSheet
import it.emanuelemelini.mitayo.ui.deck.EpisodesDialog
import it.emanuelemelini.mitayo.ui.deck.EpisodesPrompt
import it.emanuelemelini.mitayo.ui.deck.SwipeDirection
import it.emanuelemelini.mitayo.ui.shareText
import it.emanuelemelini.mitayo.ui.theme.MitayoColors
import java.text.NumberFormat
import kotlin.math.abs

private enum class CompareTab { NUMBERS, SHARED, ONLY_A, ONLY_B }

/** Two MAL lists side by side: totals, the anime in common with both scores, and the rest. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: CompareViewModel = viewModel {
        CompareViewModel(
            container.otherLists,
            container.statusEditor,
            container.settingsRepository,
            container.telemetry,
            container.catalogRepository,
            container.listRepository,
            container.deckRepository,
        )
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val details by viewModel.details.collectAsStateWithLifecycle()
    val relatedPrompt by viewModel.relatedPrompt.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(CompareTab.NUMBERS) }
    var sortByDifference by rememberSaveable { mutableStateOf(false) }
    var episodesFor by remember { mutableStateOf<MalUserAnime?>(null) }
    val me = state.myName ?: stringResource(R.string.compare_me)
    fun label(side: CompareSide) = side.name ?: me

    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_title)
    fun shareComparison(text: String) {
        container.telemetry.logShare(ShareKind.COMPARISON)
        shareText(context, text, shareTitle)
    }

    /** Watching asks the episodes first, as a swipe up does. */
    fun setStatus(entry: MalUserAnime, status: ListStatus) {
        if (status == ListStatus.WATCHING) episodesFor = entry else viewModel.setStatus(entry, status)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            item(key = "form") { FormCard(state, viewModel, Modifier.staggeredEntrance(0)) }
            state.error?.let { error ->
                item(key = "error") {
                    Text(error.asString(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.loading) {
                item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
            val result = state.result ?: return@LazyColumn
            if (state.loading) return@LazyColumn
            val labelA = label(result.a)
            val labelB = label(result.b)
            item(key = "header") { ResultHeader(labelA, labelB, result, state.affinityMethod, onShare = { text -> shareComparison(text) }) }
            item(key = "tabs") {
                PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 0.dp, containerColor = Color.Transparent) {
                    CompareTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry,
                            onClick = { tab = entry },
                            text = {
                                Text(
                                    when (entry) {
                                        CompareTab.NUMBERS -> stringResource(R.string.compare_tab_numbers)
                                        CompareTab.SHARED -> stringResource(R.string.compare_tab_shared, result.comparison.shared.size)
                                        CompareTab.ONLY_A -> stringResource(R.string.compare_tab_only, labelA, result.comparison.onlyA.size)
                                        CompareTab.ONLY_B -> stringResource(R.string.compare_tab_only, labelB, result.comparison.onlyB.size)
                                    }
                                )
                            },
                        )
                    }
                }
            }
            when (tab) {
                CompareTab.NUMBERS -> numbers(labelA, labelB, result.comparison.a, result.comparison.b)
                CompareTab.SHARED -> shared(labelA, labelB, result, state.titles, sortByDifference, viewModel::openDetails) {
                    sortByDifference = it
                }
                CompareTab.ONLY_A -> only(result.comparison.onlyA, state, result.meIsB, viewModel::openDetails, ::setStatus)
                CompareTab.ONLY_B -> only(result.comparison.onlyB, state, result.meIsA, viewModel::openDetails, ::setStatus)
            }
        }
    }

    details?.let {
        AnimeDetailsSheet(
            state = it,
            titles = state.titles,
            onDismiss = viewModel::closeDetails,
            onSetRelatedStatus = viewModel::setRelatedStatus,
        )
    }
    relatedPrompt?.let { card ->
        EpisodesDialog(
            prompt = EpisodesPrompt(card, SwipeDirection.UP),
            titles = state.titles,
            onConfirm = viewModel::confirmRelatedEpisodes,
            onDismiss = viewModel::cancelRelatedEpisodes,
        )
    }
    episodesFor?.let { entry ->
        EpisodesDialog(
            prompt = EpisodesPrompt(DeckCard(entry.node.toAnime(), null, null, null), SwipeDirection.UP),
            titles = state.titles,
            onConfirm = { episodes ->
                viewModel.setStatus(entry, ListStatus.WATCHING, episodes)
                episodesFor = null
            },
            onDismiss = { episodesFor = null },
        )
    }
}

@Composable
private fun FormCard(state: CompareUiState, viewModel: CompareViewModel, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.compare_first), style = MaterialTheme.typography.titleSmall)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(true to R.string.compare_me, false to R.string.compare_other_user).forEachIndexed { index, (meFirst, label) ->
                    SegmentedButton(
                        selected = state.form.meFirst == meFirst,
                        onClick = { viewModel.setMeFirst(meFirst) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(stringResource(label)) }
                }
            }
            if (!state.form.meFirst) {
                NameField(state.form.nameA, viewModel::setNameA, imeAction = ImeAction.Next, onDone = {})
            }
            Text(stringResource(R.string.compare_second), style = MaterialTheme.typography.titleSmall)
            NameField(state.form.nameB, viewModel::setNameB, imeAction = ImeAction.Go) {
                focusManager.clearFocus()
                viewModel.compare()
            }
            if (state.recentUsers.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.recentUsers.forEach { name ->
                        FilterChip(selected = false, onClick = { viewModel.useRecent(name) }, label = { Text(name) })
                    }
                    TextButton(onClick = viewModel::clearRecent) { Text(stringResource(R.string.compare_clear_recent)) }
                }
            }
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.compare()
                },
                enabled = !state.loading && state.form.nameB.isNotEmpty() && (state.form.meFirst || state.form.nameA.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.compare_action))
            }
            Text(
                text = stringResource(R.string.compare_public_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NameField(value: String, onChange: (String) -> Unit, imeAction: ImeAction, onDone: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.compare_name_hint)) },
        leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onGo = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultHeader(
    labelA: String,
    labelB: String,
    result: CompareResult,
    method: AffinityMethod,
    onShare: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val affinity = result.comparison.affinityBy(method)
    val affinityText = affinity?.let { String.format(locale, "%.1f%%", it) } ?: "–"
    val shareText = stringResource(
        R.string.share_comparison_text,
        labelA,
        labelB,
        affinityText,
        result.comparison.shared.size,
        AppLinks.site.toString(),
    )
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.compare_versus, labelA, labelB),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = affinityText,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(
                    when (method) {
                        AffinityMethod.SHARED_SCORES -> if (affinity != null) R.string.compare_affinity else R.string.compare_affinity_unknown
                        AffinityMethod.TASTES ->
                            if (affinity != null) R.string.compare_affinity_tastes else R.string.compare_affinity_tastes_unknown
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = pluralStringResource(R.plurals.compare_shared_count, result.comparison.shared.size, result.comparison.shared.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            TextButton(onClick = { onShare(shareText) }) {
                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.action_share))
            }
        }
    }
}

private fun LazyListScope.numbers(labelA: String, labelB: String, a: ListStats, b: ListStats) {
    item(key = "numbers") {
        val locale = LocalConfiguration.current.locales[0]
        val integer = remember(locale) { NumberFormat.getIntegerInstance(locale) }
        fun decimal(value: Double?, digits: Int) = value?.let { String.format(locale, "%.${digits}f", it) } ?: "–"
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberRow("", labelA, labelB, header = true)
                NumberRow(stringResource(R.string.compare_entries), integer.format(a.entries), integer.format(b.entries))
                ListStatus.entries.forEach { status ->
                    NumberRow(
                        listStatusLabel(status),
                        integer.format(a.byStatus[status] ?: 0),
                        integer.format(b.byStatus[status] ?: 0),
                    )
                }
                NumberRow(stringResource(R.string.compare_episodes), integer.format(a.episodes), integer.format(b.episodes))
                NumberRow(stringResource(R.string.compare_days), decimal(a.days, 1), decimal(b.days, 1))
                NumberRow(stringResource(R.string.compare_mean), decimal(a.meanScore, 2), decimal(b.meanScore, 2))
                NumberRow(stringResource(R.string.compare_scored), integer.format(a.scored), integer.format(b.scored))
            }
        }
    }
    item(key = "distribution") { ScoreDistribution(labelA, labelB, a, b) }
}

@Composable
private fun NumberRow(label: String, a: String, b: String, header: Boolean = false) {
    val style = if (header) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.4f))
        Text(a, style = style, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(b, style = style, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/** How many anime got each score, the two users as two bars. */
@Composable
private fun ScoreDistribution(labelA: String, labelB: String, a: ListStats, b: ListStats) {
    val max = (a.scoreCounts + b.scoreCounts).maxOrNull()?.takeIf { it > 0 } ?: return
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.compare_distribution), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(labelA, MaterialTheme.colorScheme.primary)
                Legend(labelB, MitayoColors.Kin)
            }
            (10 downTo 1).forEach { score ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$score", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(24.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Bar(a.scoreCounts[score - 1].toFloat() / max, MaterialTheme.colorScheme.primary)
                        Bar(b.scoreCounts[score - 1].toFloat() / max, MitayoColors.Kin)
                    }
                    Text(
                        text = "${a.scoreCounts[score - 1]} · ${b.scoreCounts[score - 1]}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(64.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color)
    )
}

private fun LazyListScope.shared(
    labelA: String,
    labelB: String,
    result: CompareResult,
    titles: TitleChoice,
    sortByDifference: Boolean,
    onOpenDetails: (MalAnime) -> Unit,
    onSortChange: (Boolean) -> Unit,
) {
    val comparison = result.comparison
    item(key = "shared_summary") {
        val locale = LocalConfiguration.current.locales[0]
        fun decimal(value: Double?) = value?.let { String.format(locale, "%.2f", it) } ?: "–"
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(
                    R.string.compare_shared_means,
                    labelA, decimal(comparison.sharedMeanA), labelB, decimal(comparison.sharedMeanB), decimal(comparison.meanDifference),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !sortByDifference, onClick = { onSortChange(false) }, label = { Text(stringResource(R.string.compare_sort_title)) })
                FilterChip(selected = sortByDifference, onClick = { onSortChange(true) }, label = { Text(stringResource(R.string.compare_sort_difference)) })
            }
        }
    }
    val rows = if (sortByDifference) {
        comparison.shared.sortedByDescending { it.difference?.let(::abs) ?: -1 }
    } else {
        comparison.shared
    }
    items(rows, key = { "shared_${it.anime.id}" }) { SharedRow(it, labelA, labelB, titles) { onOpenDetails(it.anime) } }
}

@Composable
private fun SharedRow(shared: SharedAnime, labelA: String, labelB: String, titles: TitleChoice, onOpenDetails: () -> Unit) {
    val anime = remember(shared.anime.id) { shared.anime.toAnime() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Poster(anime.pictureUrl, onOpenDetails)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(anime.displayTitle(titles), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                text = "$labelA: ${malEntryLine(shared.a)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$labelB: ${malEntryLine(shared.b)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        shared.difference?.let { difference ->
            Spacer(Modifier.width(8.dp))
            Text(
                text = when {
                    difference > 0 -> "+$difference"
                    difference < 0 -> "−${-difference}"
                    else -> "="
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (difference == 0) MitayoColors.Matcha else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun LazyListScope.only(
    entries: List<MalUserAnime>,
    state: CompareUiState,
    canSetStatus: Boolean,
    onOpenDetails: (MalAnime) -> Unit,
    onSetStatus: (MalUserAnime, ListStatus) -> Unit,
) {
    if (entries.isEmpty()) {
        item(key = "only_empty") {
            Text(
                text = stringResource(R.string.compare_only_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        return
    }
    items(entries, key = { "only_${it.node.id}" }) { entry ->
        OnlyRow(
            entry = entry,
            titles = state.titles,
            mine = state.setStatuses[entry.node.id],
            canSetStatus = canSetStatus,
            onOpenDetails = { onOpenDetails(entry.node) },
            onSetStatus = { status -> onSetStatus(entry, status) },
        )
    }
}

@Composable
private fun OnlyRow(
    entry: MalUserAnime,
    titles: TitleChoice,
    mine: ListStatus?,
    canSetStatus: Boolean,
    onOpenDetails: () -> Unit,
    onSetStatus: (ListStatus) -> Unit,
) {
    val anime = remember(entry.node.id) { entry.node.toAnime() }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Poster(anime.pictureUrl, onOpenDetails)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(anime.displayTitle(titles), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            anime.subtitle(titles)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = malEntryLine(entry.listStatus),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            mine?.let {
                Text(
                    text = stringResource(R.string.deck_in_your_list, listStatusLabel(it)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (canSetStatus) {
            Spacer(Modifier.width(8.dp))
            StatusMenu(current = mine, canComplete = !anime.isAiring, onSetStatus = onSetStatus)
        }
    }
}

/** Tapped, it opens the details of the anime, as ⓘ in a deck. */
@Composable
private fun Poster(url: String?, onClick: () -> Unit) {
    AsyncImage(
        model = url,
        contentDescription = stringResource(R.string.action_details),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(width = 44.dp, height = 62.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick),
    )
}
