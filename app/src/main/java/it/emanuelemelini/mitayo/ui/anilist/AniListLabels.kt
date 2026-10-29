package it.emanuelemelini.mitayo.ui.anilist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.emanuelemelini.mitayo.R
import it.emanuelemelini.mitayo.data.anilist.AniListSide
import it.emanuelemelini.mitayo.data.anilist.AniListStatus
import it.emanuelemelini.mitayo.data.anilist.ScoreFormat
import it.emanuelemelini.mitayo.data.anilist.Scores
import java.util.Locale

/** AniList's own names of its statuses, as MAL's are shown with MAL's. */
fun aniListStatusRes(status: AniListStatus): Int = when (status) {
    AniListStatus.CURRENT -> R.string.anilist_status_current
    AniListStatus.PLANNING -> R.string.anilist_status_planning
    AniListStatus.COMPLETED -> R.string.anilist_status_completed
    AniListStatus.DROPPED -> R.string.anilist_status_dropped
    AniListStatus.PAUSED -> R.string.anilist_status_paused
    AniListStatus.REPEATING -> R.string.anilist_status_repeating
}

fun scoreFormatRes(format: ScoreFormat): Int = when (format) {
    ScoreFormat.POINT_100 -> R.string.anilist_format_100
    ScoreFormat.POINT_10_DECIMAL -> R.string.anilist_format_10_decimal
    ScoreFormat.POINT_10 -> R.string.anilist_format_10
    ScoreFormat.POINT_5 -> R.string.anilist_format_5
    ScoreFormat.POINT_3 -> R.string.anilist_format_3
}

/** The score as AniList shows it to the user: 85/100, ★ 8.5, ★ 9, ★★★★ or a smiley; null without a score. */
fun aniListScoreLabel(side: AniListSide, format: ScoreFormat): String? {
    val raw = side.scoreRaw
    if (raw <= 0) return null
    return when (format) {
        ScoreFormat.POINT_100 -> "$raw/100"
        ScoreFormat.POINT_10_DECIMAL -> "★ " + String.format(Locale.ROOT, "%.1f", raw / 10.0)
        ScoreFormat.POINT_10 -> "★ ${Scores.toMal(raw)}"
        ScoreFormat.POINT_5 -> "★".repeat(Scores.stars(raw))
        ScoreFormat.POINT_3 -> when (Scores.smiley(raw)) {
            1 -> "🙁"
            2 -> "😐"
            else -> "🙂"
        }
    }
}

/** "Rewatching · 3/12 · ★★★★" for an anime of the AniList list. */
@Composable
fun aniListLine(side: AniListSide, totalEpisodes: Int, format: ScoreFormat): String = listOfNotNull(
    stringResource(aniListStatusRes(side.status)),
    when {
        side.progress <= 0 -> null
        totalEpisodes > 0 -> "${side.progress}/$totalEpisodes"
        else -> "${side.progress}"
    },
    aniListScoreLabel(side, format),
).joinToString(" · ")
