package it.emanuelemelini.mitayo.ui.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.SyncState
import it.emanuelemelini.mitayo.data.db.DecadeCount
import it.emanuelemelini.mitayo.data.db.GenreCount
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.animatedProgress
import it.emanuelemelini.mitayo.ui.components.countUp
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import it.emanuelemelini.mitayo.ui.theme.MitayoColors
import it.emanuelemelini.mitayo.ui.toUiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onOpenCategory: (Category) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenUserDeck: () -> Unit,
) {
    val container = appContainer()
    val viewModel: CategoriesViewModel = viewModel {
        CategoriesViewModel(container.settingsRepository, container.deckRepository, container.listRepository, container.syncManager)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // Only on the way in: coming back from a deck the screen is already there
    val firstVisit = rememberSaveable { mutableStateOf(true) }
    val animateEntrance = remember { firstVisit.value }
    LaunchedEffect(Unit) { firstVisit.value = false }
    val entrance: Modifier.(Int) -> Modifier = { index -> if (animateEntrance) staggeredEntrance(index) else this }

    PullToRefreshBox(
        isRefreshing = state.isSyncing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            fullWidth("header") { Header(state, Modifier.entrance(0)) }
            fullWidth("search") { SearchField(onClick = dropUnlessResumed(block = onOpenSearch), modifier = Modifier.entrance(1)) }
            fullWidth("sync") { SyncBanner(state.sync, onRetry = viewModel::refresh) }
            fullWidth("specials_title") { SectionTitle(stringResource(R.string.categories_specials), Modifier.entrance(2)) }
            fullWidth("specials") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(Category.specials, key = { _, category -> category.key }) { index, category ->
                        SpecialCard(category, onClick = { onOpenCategory(category) }, modifier = Modifier.entrance(3 + index))
                    }
                }
            }
            statusSection(state.statuses, onOpenCategory, entrance)
            fullWidth("others_title") { SectionTitle(stringResource(R.string.categories_others)) }
            fullWidth("others") {
                OthersRow(
                    onOpenCompare = dropUnlessResumed(block = onOpenCompare),
                    onOpenUserDeck = dropUnlessResumed(block = onOpenUserDeck),
                    modifier = Modifier.entrance(3 + Category.specials.size),
                )
            }
            decadeSection(state.decades, onOpenCategory, entrance)
            genreSection("genres", R.string.categories_genres, state.genres, onOpenCategory, entrance)
            genreSection("themes", R.string.categories_themes, state.themes, onOpenCategory, entrance)
            genreSection("demographics", R.string.categories_demographics, state.demographics, onOpenCategory, entrance)
            if (state.loaded && state.genres.isEmpty() && !state.isSyncing) {
                fullWidth("empty") {
                    Text(
                        text = stringResource(R.string.categories_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** The user's own list, one deck per status: to go through it again, change status, give scores. */
private fun LazyGridScope.statusSection(
    statuses: Map<ListStatus, Int>,
    onOpenCategory: (Category) -> Unit,
    entrance: Modifier.(Int) -> Modifier,
) {
    if (statuses.isEmpty()) return
    fullWidth("statuses_title") { SectionTitle(stringResource(R.string.categories_my_list)) }
    itemsIndexed(ListStatus.entries, key = { _, status -> "status_${status.apiValue}" }) { index, status ->
        val category = Category.OfStatus(status)
        CountTile(
            icon = category.icon(),
            name = category.title(),
            remainingCount = statuses[status] ?: 0,
            countKey = "remaining_${category.key}",
            onClick = { onOpenCategory(category) },
            modifier = Modifier.animateItem().entrance(3 + index),
            countRes = R.plurals.categories_in_list,
        )
    }
}

/** The years the anime started, for who's catching up on the past ones. */
private fun LazyGridScope.decadeSection(
    decades: List<DecadeCount>,
    onOpenCategory: (Category) -> Unit,
    entrance: Modifier.(Int) -> Modifier,
) {
    if (decades.isEmpty()) return
    fullWidth("decades_title") { SectionTitle(stringResource(R.string.categories_decades)) }
    itemsIndexed(decades, key = { _, decade -> "decade_${decade.start}" }) { index, decade ->
        val category = Category.OfDecade(decade.start)
        CountTile(
            icon = category.icon(),
            name = category.title(),
            remainingCount = decade.remaining,
            countKey = "remaining_${category.key}",
            onClick = { onOpenCategory(category) },
            modifier = Modifier.animateItem().entrance(5 + index),
        )
    }
}

private fun LazyGridScope.genreSection(
    key: String,
    titleRes: Int,
    genres: List<GenreCount>,
    onOpenCategory: (Category) -> Unit,
    entrance: Modifier.(Int) -> Modifier,
) {
    if (genres.isEmpty()) return
    fullWidth("${key}_title") { SectionTitle(stringResource(titleRes)) }
    itemsIndexed(genres, key = { _, genre -> "genre_${genre.id}" }) { index, genre ->
        GenreTile(
            genre = genre,
            onClick = { onOpenCategory(Category.OfGenre(genre.id)) },
            // A finished genre moves down the grid smoothly instead of jumping
            modifier = Modifier.animateItem().entrance(5 + index),
        )
    }
}

@Composable
private fun Header(state: CategoriesUiState, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Text(
            text = state.userName?.let { stringResource(R.string.categories_hello, it) } ?: stringResource(R.string.nav_categories),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.completedCount > 0) {
            val completed = countUp(state.completedCount, label = "completed")
            Text(
                text = pluralStringResource(R.plurals.categories_completed_count, completed, completed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** With other MAL users: comparing two lists, and a deck made of someone else's list. */
@Composable
private fun OthersRow(onOpenCompare: () -> Unit, onOpenUserDeck: () -> Unit, modifier: Modifier = Modifier) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth()) {
        OthersCard(
            icon = Icons.AutoMirrored.Rounded.CompareArrows,
            title = stringResource(R.string.compare_title),
            body = stringResource(R.string.categories_compare_body),
            onClick = onOpenCompare,
            modifier = Modifier.weight(1f),
        )
        OthersCard(
            icon = Icons.Rounded.Groups,
            title = stringResource(R.string.user_deck_title),
            body = stringResource(R.string.categories_user_deck_body),
            onClick = onOpenUserDeck,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OthersCard(icon: ImageVector, title: String, body: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.height(120.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(26.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Looks like a search field; the search itself is a page of its own, with the keyboard open. */
@Composable
private fun SearchField(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncBanner(sync: SyncState, onRetry: () -> Unit) {
    // Opens and closes instead of popping in and out
    AnimatedVisibility(
        visible = sync != SyncState.Idle,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        SyncBannerContent(sync, onRetry)
    }
}

@Composable
private fun SyncBannerContent(sync: SyncState, onRetry: () -> Unit) {
    when (sync) {
        SyncState.Idle -> Unit
        SyncState.UserList -> ProgressBanner(stringResource(R.string.sync_list), progress = null)
        is SyncState.Catalog -> ProgressBanner(
            text = stringResource(R.string.sync_catalog, sync.loaded, sync.total),
            progress = sync.loaded.toFloat() / sync.total,
        )
        is SyncState.Failed -> Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = sync.error.toUiText().asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }
    }
}

@Composable
private fun ProgressBanner(text: String, progress: Float?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            if (progress == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                val animated = animatedProgress(progress, label = "catalog")
                LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun SpecialCard(category: Category, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.width(132.dp).height(104.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(category.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(26.dp))
            Text(
                text = category.title(),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun GenreTile(genre: GenreCount, onClick: () -> Unit, modifier: Modifier = Modifier) {
    CountTile(genreIcon(genre.id), genre.name, genre.remaining, "remaining_${genre.id}", onClick, modifier)
}

/** A category with how many of its anime are left; [countKey] names the count animation. */
@Composable
private fun CountTile(
    icon: ImageVector,
    name: String,
    remainingCount: Int,
    countKey: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** "12 to swipe" for the catalog decks, "12 anime" for the decks of the user's list. */
    countRes: Int = R.plurals.categories_remaining,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            Column {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val remaining = countUp(remainingCount, label = countKey)
                Text(
                    text = if (remainingCount > 0 || countRes != R.plurals.categories_remaining) {
                        pluralStringResource(countRes, remaining, remaining)
                    } else {
                        stringResource(R.string.categories_all_done)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remainingCount > 0 || countRes != R.plurals.categories_remaining) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MitayoColors.Matcha
                    },
                )
            }
        }
    }
}
