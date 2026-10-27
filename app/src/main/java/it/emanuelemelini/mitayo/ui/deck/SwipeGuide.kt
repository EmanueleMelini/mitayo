package it.emanuelemelini.mitayo.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.ui.components.staggeredEntrance
import it.emanuelemelini.mitayo.ui.theme.MitayoColors

/**
 * What each swipe does, over the first deck ever opened; it goes away with "Got it".
 * [leftPasses]: a deck by status, where left only moves on.
 */
@Composable
fun SwipeGuide(leftPasses: Boolean, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MitayoColors.Sumi.copy(alpha = 0.72f))
            // The deck underneath doesn't get the touches
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.padding(24.dp).widthIn(max = 460.dp).staggeredEntrance(0),
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.guide_title), style = MaterialTheme.typography.titleLarge)
                GuideRow(Icons.AutoMirrored.Rounded.ArrowForward, MitayoColors.Matcha, stringResource(R.string.guide_right))
                GuideRow(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    if (leftPasses) MitayoColors.Murasaki else MitayoColors.Aka,
                    stringResource(if (leftPasses) R.string.guide_left_pass else R.string.guide_left),
                )
                GuideRow(Icons.Rounded.ArrowUpward, MitayoColors.Kin, stringResource(R.string.guide_up))
                GuideRow(Icons.Rounded.Star, MitayoColors.Kin, stringResource(R.string.guide_score))
                GuideRow(Icons.Rounded.MoreHoriz, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.guide_more))
                GuideRow(Icons.Rounded.TouchApp, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.guide_tap))
                GuideRow(Icons.AutoMirrored.Rounded.Undo, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.guide_undo))
                Spacer(Modifier.height(4.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_ok)) }
            }
        }
    }
}

@Composable
private fun GuideRow(icon: ImageVector, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
