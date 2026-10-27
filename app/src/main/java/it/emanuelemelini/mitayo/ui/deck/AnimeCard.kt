package it.emanuelemelini.mitayo.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.ui.components.listStatusLine
import it.emanuelemelini.mitayo.ui.components.metaLine
import it.emanuelemelini.mitayo.ui.components.scoreLabel
import it.emanuelemelini.mitayo.ui.theme.MitayoColors

val CardShape = RoundedCornerShape(28.dp)

/** Poster card. Text is always light on a dark scrim, in both themes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeCard(
    card: DeckCard,
    titles: TitleChoice,
    modifier: Modifier = Modifier,
    /** Another user's deck: "Name: Completed · ★ 9". */
    note: String? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val anime = card.anime
    Box(
        modifier
            .fillMaxSize()
            .shadow(10.dp, CardShape)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = anime.pictureUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MitayoColors.Sumi.copy(alpha = 0.94f))))
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            card.listStatus?.let { status ->
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f), contentColor = Color.White) {
                    Text(
                        text = stringResource(R.string.deck_in_your_list, listStatusLine(status, card.listEpisodes, anime.episodes, card.listScore)),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            note?.let {
                Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.18f), contentColor = Color.White) {
                    Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            Text(
                text = anime.displayTitle(titles),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            anime.subtitle(titles)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = metaLine(anime),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.82f),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                anime.mean?.let { mean ->
                    Icon(Icons.Rounded.Star, contentDescription = null, tint = MitayoColors.Kin, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = scoreLabel(mean),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                if (anime.popularity != Int.MAX_VALUE) {
                    Text(
                        text = stringResource(R.string.deck_popularity, anime.popularity),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            if (anime.genreNames.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    anime.genreNames.split(", ").take(4).forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(MitayoColors.Murasaki.copy(alpha = 0.55f))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        overlay()
    }
}

/**
 * The "watched / skip / watching" stamps that appear while dragging, faded in with [state].
 * [leftPasses]: a deck by status, where left only moves on to the next card. [planToWatch]: an
 * anime only announced, which right and up set as plan to watch.
 */
@Composable
fun BoxScope.SwipeStamps(state: SwipeState, leftPasses: Boolean, planToWatch: Boolean) {
    Stamp(
        text = stringResource(if (planToWatch) R.string.stamp_plan else R.string.stamp_completed),
        color = if (planToWatch) MitayoColors.Sakura else MitayoColors.Matcha,
        rotation = -14f,
        alpha = state.progress(SwipeDirection.RIGHT),
        modifier = Modifier.align(Alignment.TopStart).padding(28.dp),
    )
    Stamp(
        text = stringResource(if (leftPasses) R.string.stamp_next else R.string.stamp_skip),
        color = if (leftPasses) MitayoColors.Murasaki else MitayoColors.Aka,
        rotation = 14f,
        alpha = state.progress(SwipeDirection.LEFT),
        modifier = Modifier.align(Alignment.TopEnd).padding(28.dp),
    )
    Stamp(
        text = stringResource(if (planToWatch) R.string.stamp_plan else R.string.stamp_watching),
        color = if (planToWatch) MitayoColors.Sakura else MitayoColors.Kin,
        rotation = -6f,
        alpha = state.progress(SwipeDirection.UP),
        modifier = Modifier.align(Alignment.Center),
    )
}

@Composable
private fun Stamp(text: String, color: Color, rotation: Float, alpha: Float, modifier: Modifier) {
    if (alpha <= 0f) return
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .graphicsLayer {
                rotationZ = rotation
                this.alpha = alpha
            }
            .background(MitayoColors.Sumi.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .border(4.dp, color, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}
