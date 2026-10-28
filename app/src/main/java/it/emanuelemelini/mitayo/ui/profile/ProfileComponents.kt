package it.emanuelemelini.mitayo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.emanuelemelini.mitayo.ui.components.countUp
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Pieces shared by the Profile menu and its pages. */

@Composable
internal fun Avatar(name: String?, picture: String?) {
    Box(
        modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name?.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (picture != null) {
            AsyncImage(model = picture, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/** The number counts up to its value. */
@Composable
internal fun StatCard(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = NumberFormat.getIntegerInstance().format(countUp(value, label = label)),
                style = MaterialTheme.typography.headlineSmall,
                color = color,
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A group of rows on a page, on a card. */
@Composable
internal fun PageCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = modifier) {
        content()
    }
}

/** A title, a line of explanation and a segmented choice; [note] goes under the choice. */
@Composable
internal fun <T> ChoiceRow(
    title: String,
    body: String?,
    options: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit,
    note: String? = null,
) {
    Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(stringResource(label), maxLines = 1) }
            }
        }
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun LinkRow(icon: ImageVector, title: String, body: String?, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = body?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(onClick = onClick),
    )
}

@Composable
internal fun SwitchRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(body) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickableRow(enabled = enabled) { onCheckedChange(!checked) },
    )
}

internal fun Modifier.clickableRow(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled, onClick = onClick)

internal fun formatTime(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
