package it.emanuelemelini.mitayo.data.anilist

import it.emanuelemelini.mitayo.data.db.ListStatus

/** The statuses of an AniList list, named as the API names them. */
enum class AniListStatus {
    CURRENT,
    PLANNING,
    COMPLETED,
    DROPPED,
    PAUSED,
    REPEATING;

    /** MAL has no rewatching status (only a flag Mitayo doesn't use): a rewatch counts as completed. */
    fun toMal(): ListStatus = when (this) {
        CURRENT -> ListStatus.WATCHING
        PLANNING -> ListStatus.PLAN_TO_WATCH
        COMPLETED, REPEATING -> ListStatus.COMPLETED
        DROPPED -> ListStatus.DROPPED
        PAUSED -> ListStatus.ON_HOLD
    }

    /** The same status as [status] on MAL; a rewatch is a completed anime watched again. */
    fun matches(status: ListStatus): Boolean = toMal() == status

    companion object {
        fun fromMal(status: ListStatus): AniListStatus = when (status) {
            ListStatus.WATCHING -> CURRENT
            ListStatus.COMPLETED -> COMPLETED
            ListStatus.ON_HOLD -> PAUSED
            ListStatus.DROPPED -> DROPPED
            ListStatus.PLAN_TO_WATCH -> PLANNING
        }

        fun fromApi(value: String?): AniListStatus? = entries.firstOrNull { it.name == value }
    }
}

/** How the user scores on AniList, chosen there; the API gives every score on 0-100 too. */
enum class ScoreFormat {
    POINT_100,
    POINT_10_DECIMAL,
    POINT_10,
    POINT_5,
    POINT_3;

    companion object {
        fun fromApi(value: String?): ScoreFormat = entries.firstOrNull { it.name == value } ?: POINT_10
    }
}

/** MAL scores (whole numbers from 1 to 10, 0 = no score) and AniList's on 0-100. */
object Scores {
    /** Exact: 8 on MAL is 80, which AniList shows as 8, 8.0, 80 or 4 stars. */
    fun toAniList(malScore: Int): Int = malScore * 10

    /** Rounded half up (85 is 9); a score never turns into "no score". */
    fun toMal(raw: Int): Int = if (raw <= 0) 0 else ((raw + 5) / 10).coerceIn(1, 10)

    /**
     * The same score on the coarser of the two scales, the user's AniList [format] or MAL's: 9 on
     * MAL and 85 on AniList match, and so do 7 and 4 stars.
     */
    fun match(malScore: Int, raw: Int, format: ScoreFormat): Boolean {
        if (malScore == 0 || raw == 0) return malScore == 0 && raw == 0
        return when (format) {
            ScoreFormat.POINT_100, ScoreFormat.POINT_10_DECIMAL, ScoreFormat.POINT_10 -> toMal(raw) == malScore
            ScoreFormat.POINT_5 -> stars(raw) == stars(toAniList(malScore))
            ScoreFormat.POINT_3 -> smiley(raw) == smiley(toAniList(malScore))
        }
    }

    /** Stars as AniList shows them (checked on its API): 0-100 / 20 rounded half up, so 85 is 4, 90 is 5. */
    fun stars(raw: Int): Int = ((raw + 10) / 20).coerceIn(1, 5)

    /**
     * Smileys as AniList shows them (checked on its API: 35 is sad, 40 to 60 neutral, from 70
     * happy); AniList saves a chosen smiley as 35, 60 or 85.
     */
    fun smiley(raw: Int): Int = when {
        raw >= SMILEY_HAPPY -> 3
        raw >= SMILEY_NEUTRAL -> 2
        else -> 1
    }

    private const val SMILEY_NEUTRAL = 36
    private const val SMILEY_HAPPY = 61
}
