package it.emanuelemelini.mitayo.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.ListStats
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.ui.appContainer
import it.emanuelemelini.mitayo.ui.components.listStatusLabel
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import it.emanuelemelini.mitayo.ui.theme.MitayoColors
import java.text.NumberFormat

/** The user's own list in numbers: days, episodes, statuses, scores and the genres seen most. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: StatsViewModel = viewModel { StatsViewModel(container.otherLists) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed(block = onBack)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val stats = state.stats
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.stats_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            stats == null -> Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.error?.asString().orEmpty(), textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = viewModel::load) { Text(stringResource(R.string.action_retry)) }
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                item(key = "hero") { Hero(stats, Modifier.staggeredEntrance(0)) }
                item(key = "statuses") { Statuses(stats, Modifier.staggeredEntrance(1)) }
                if (stats.scored > 0) item(key = "scores") { Scores(stats, Modifier.staggeredEntrance(2)) }
                if (state.genres.isNotEmpty()) item(key = "genres") { Genres(state.genres, Modifier.staggeredEntrance(3)) }
            }
        }
    }
}

/** Days watched in big, then episodes, anime and mean score. */
@Composable
private fun Hero(stats: ListStats, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val integer = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format(locale, "%.1f", stats.days),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.stats_days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                HeroNumber(integer.format(stats.entries), stringResource(R.string.compare_entries), Modifier.weight(1f))
                HeroNumber(integer.format(stats.episodes), stringResource(R.string.compare_episodes), Modifier.weight(1f))
                HeroNumber(stats.meanScore?.let { String.format(locale, "%.2f", it) } ?: "–", stringResource(R.string.compare_mean), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroNumber(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Statuses(stats: ListStats, modifier: Modifier = Modifier) {
    val max = stats.byStatus.values.maxOrNull()?.takeIf { it > 0 } ?: return
    StatsCard(stringResource(R.string.stats_statuses), modifier) {
        ListStatus.entries.forEach { status ->
            val count = stats.byStatus[status] ?: 0
            BarRow(listStatusLabel(status), count, count.toFloat() / max, MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Scores(stats: ListStats, modifier: Modifier = Modifier) {
    val max = stats.scoreCounts.maxOrNull()?.takeIf { it > 0 } ?: return
    StatsCard(stringResource(R.string.compare_distribution), modifier) {
        (10 downTo 1).forEach { score ->
            val count = stats.scoreCounts[score - 1]
            BarRow("$score", count, count.toFloat() / max, MitayoColors.Kin, labelWidth = 32)
        }
    }
}

@Composable
private fun Genres(genres: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val max = genres.first().second
    StatsCard(stringResource(R.string.stats_genres), modifier) {
        genres.forEach { (name, count) -> BarRow(name, count, count.toFloat() / max, MitayoColors.Murasaki) }
    }
}

@Composable
private fun StatsCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

/** A label, a bar as long as [fraction] of the biggest one, and the number. */
@Composable
private fun BarRow(label: String, count: Int, fraction: Float, color: Color, labelWidth: Int = 110) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth.dp),
        )
        Box(Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
    }
}
