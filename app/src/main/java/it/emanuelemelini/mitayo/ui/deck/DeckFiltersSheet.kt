package it.emanuelemelini.mitayo.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.DeckFilters
import it.emanuelemelini.mitayo.data.MediaKind
import it.emanuelemelini.mitayo.ui.components.mediaTypeLabel
import java.time.LocalDate
import kotlin.math.roundToInt

/** Lowest MAL scores offered: "any", then these. */
private val MIN_SCORES = listOf(6, 7, 8, 9)

/** Oldest year of the slider: older anime are few, they go with it. */
private const val FIRST_YEAR = 1960

/** The filters of the open deck, applied at once while they change. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeckFiltersSheet(filters: DeckFilters, onChange: (DeckFilters) -> Unit, onDismiss: () -> Unit) {
    // Next year too: the anime already announced
    val lastYear = remember { LocalDate.now().year + 1 }
    val fullRange = FIRST_YEAR.toFloat()..lastYear.toFloat()
    // The slider moves freely; the deck changes when it's let go
    var years by remember(filters.years) {
        mutableStateOf(filters.years?.let { it.first.toFloat()..it.last.toFloat() } ?: fullRange)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
        contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.filters_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.filters_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (filters.active) {
                    TextButton(onClick = { onChange(DeckFilters()) }) { Text(stringResource(R.string.filters_reset)) }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.filters_kind), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MediaKind.entries.forEach { kind ->
                    val selected = kind in filters.kinds
                    FilterChip(
                        selected = selected,
                        onClick = { onChange(filters.copy(kinds = if (selected) filters.kinds - kind else filters.kinds + kind)) },
                        label = { Text(mediaTypeLabel(kind.malTypes.first()).orEmpty()) },
                    )
                }
            }

            Text(stringResource(R.string.filters_min_score), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.minScore == null,
                    onClick = { onChange(filters.copy(minScore = null)) },
                    label = { Text(stringResource(R.string.filters_any)) },
                )
                MIN_SCORES.forEach { score ->
                    FilterChip(
                        selected = filters.minScore == score,
                        onClick = { onChange(filters.copy(minScore = score)) },
                        label = { Text(stringResource(R.string.filters_score, score)) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filters_years), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text(
                    text = if (years == fullRange) {
                        stringResource(R.string.filters_any)
                    } else {
                        stringResource(R.string.filters_years_range, years.start.roundToInt(), years.endInclusive.roundToInt())
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RangeSlider(
                value = years,
                onValueChange = { years = it },
                valueRange = fullRange,
                onValueChangeFinished = {
                    val range = years.start.roundToInt()..years.endInclusive.roundToInt()
                    onChange(filters.copy(years = range.takeIf { it.first > FIRST_YEAR || it.last < lastYear }))
                },
            )
        }
    }
}
