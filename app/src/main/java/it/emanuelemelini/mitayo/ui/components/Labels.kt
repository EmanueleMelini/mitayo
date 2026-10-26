package it.emanuelemelini.mitayo.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalListStatus

/** MAL media types: the common ones translated, the acronyms (OVA, ONA…) as they are. */
@Composable
fun mediaTypeLabel(mediaType: String?): String? = when (mediaType) {
    null, "", "unknown" -> null
    "tv" -> "TV"
    "movie" -> stringResource(R.string.media_movie)
    "special", "tv_special" -> stringResource(R.string.media_special)
    "music" -> stringResource(R.string.media_music)
    else -> mediaType.uppercase()
}

/** Also for the season notification, outside composition. */
fun seasonNameRes(season: String): Int? = when (season) {
    "winter" -> R.string.season_winter
    "spring" -> R.string.season_spring
    "summer" -> R.string.season_summer
    "fall" -> R.string.season_fall
    else -> null
}

@Composable
fun seasonName(season: String): String? = seasonNameRes(season)?.let { stringResource(it) }

@Composable
fun seasonLabel(season: String?, year: Int?): String? {
    year ?: return null
    val name = season?.let { seasonName(it) } ?: return "$year"
    return stringResource(R.string.season_format, name, year)
}

@Composable
fun episodesLabel(anime: Anime): String? = when {
    anime.episodes > 0 -> pluralStringResource(R.plurals.episodes, anime.episodes, anime.episodes)
    anime.airingStatus == "currently_airing" -> stringResource(R.string.airing_now)
    anime.airingStatus == "not_yet_aired" -> stringResource(R.string.not_yet_aired)
    else -> null
}

/** "TV · 24 episodes · Fall 2026". */
@Composable
fun metaLine(anime: Anime): String =
    listOfNotNull(mediaTypeLabel(anime.mediaType), episodesLabel(anime), seasonLabel(anime.season, anime.seasonYear))
        .joinToString(" · ")

/** MAL score with two decimals, in the app language ("8,75" in Italian). */
@Composable
fun scoreLabel(mean: Double): String = String.format(LocalConfiguration.current.locales[0], "%.2f", mean)

fun listStatusRes(status: ListStatus): Int = when (status) {
    ListStatus.WATCHING -> R.string.status_watching
    ListStatus.COMPLETED -> R.string.status_completed
    ListStatus.ON_HOLD -> R.string.status_on_hold
    ListStatus.DROPPED -> R.string.status_dropped
    ListStatus.PLAN_TO_WATCH -> R.string.status_plan_to_watch
}

@Composable
fun listStatusLabel(status: ListStatus): String = stringResource(listStatusRes(status))

/** "Watching · 12/24 · ★ 8" for the anime already in the user's list. */
@Composable
fun listStatusLine(status: ListStatus, episodesWatched: Int?, totalEpisodes: Int, score: Int? = null): String {
    val watched = episodesWatched ?: 0
    return listOfNotNull(
        listStatusLabel(status),
        when {
            watched <= 0 -> null
            totalEpisodes > 0 -> "$watched/$totalEpisodes"
            else -> "$watched"
        },
        score?.takeIf { it > 0 }?.let { "★ $it" },
    ).joinToString(" · ")
}

/** One icon per MAL status: menus, decks by status. */
fun listStatusIcon(status: ListStatus): ImageVector = when (status) {
    ListStatus.WATCHING -> Icons.Rounded.PlayArrow
    ListStatus.COMPLETED -> Icons.Rounded.CheckCircle
    ListStatus.ON_HOLD -> Icons.Rounded.PauseCircle
    ListStatus.DROPPED -> Icons.Rounded.Block
    ListStatus.PLAN_TO_WATCH -> Icons.Rounded.BookmarkAdd
}

/** "Completed · ★ 8": an entry of anyone's list. */
@Composable
fun malEntryLine(status: MalListStatus): String = listOfNotNull(
    ListStatus.fromApi(status.status)?.let { listStatusLabel(it) },
    status.score.takeIf { it > 0 }?.let { "★ $it" },
).joinToString(" · ")

/** MAL's names of the scores, from 1 (appalling) to 10 (masterpiece). */
fun scoreNameRes(score: Int): Int = when (score) {
    10 -> R.string.score_10
    9 -> R.string.score_9
    8 -> R.string.score_8
    7 -> R.string.score_7
    6 -> R.string.score_6
    5 -> R.string.score_5
    4 -> R.string.score_4
    3 -> R.string.score_3
    2 -> R.string.score_2
    else -> R.string.score_1
}
