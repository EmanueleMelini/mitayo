package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.db.ListStatus
import java.time.LocalDate

/** What a deck shows. [key] goes in the navigation route and names the remote lists. */
sealed interface Category {
    val key: String

    /** The list comes from MAL as is (and is cached), instead of from the local catalog. */
    val isRemote: Boolean get() = this == Season || this == Upcoming || this == Suggested || this is OfSeason || this is OfUser

    /** The anime of the user's own list: a left swipe only moves on to the next one. */
    val isOwnList: Boolean get() = this is OfStatus || this == Unrated

    data object Popular : Category {
        override val key = "popular"
    }

    data object TopRated : Category {
        override val key = "top"
    }

    data object Airing : Category {
        override val key = "airing"
    }

    data object Season : Category {
        override val key = "season"
    }

    /** Announced anime, the most awaited first (MAL ranking "upcoming"). */
    data object Upcoming : Category {
        override val key = "upcoming"
    }

    data object Movies : Category {
        override val key = "movies"
    }

    data object Suggested : Category {
        override val key = "suggested"
    }

    /** Another season (past or next) from MAL's seasonal list; one at a time stays on the phone. */
    data class OfSeason(val year: Int, val season: String) : Category {
        override val key: String get() = "$SEASON_PREFIX$year:$season"

        companion object {
            val SEASONS = listOf("winter", "spring", "summer", "fall")
            const val FIRST_YEAR = 1970

            fun isValid(year: Int, season: String): Boolean = year >= FIRST_YEAR && season in SEASONS
        }
    }

    /** A MAL genre, theme or demographic. */
    data class OfGenre(val genreId: Int) : Category {
        override val key: String get() = "$GENRE_PREFIX$genreId"
    }

    /** Anime that started in the ten years from [start] (2010: 2010 to 2019), or [BEFORE] [FIRST]. */
    data class OfDecade(val start: Int) : Category {
        override val key: String get() = "$DECADE_PREFIX$start"

        val years: IntRange get() = if (start == BEFORE) 0 until FIRST else start..start + 9

        companion object {
            /** The older anime, all together: there are few of them in the catalog. */
            const val BEFORE = 0
            const val FIRST = 1980

            fun isValid(start: Int): Boolean = start == BEFORE || (start >= FIRST && start % 10 == 0)
        }
    }

    /**
     * The anime of another MAL user's list that aren't in the user's own at all: downloaded at
     * every opening, kept only while needed (one such list at a time).
     */
    data class OfUser(val userName: String) : Category {
        override val key: String get() = "$USER_PREFIX$userName"
    }

    /** The anime of the user's list with [status]: to go through them again, change status, give a score. */
    data class OfStatus(val status: ListStatus) : Category {
        override val key: String get() = "$STATUS_PREFIX${status.apiValue}"
    }

    /** The anime of the list seen or started (not plan to watch) that have no score yet. */
    data object Unrated : Category {
        override val key = "unrated"

        val statuses = listOf(ListStatus.WATCHING, ListStatus.COMPLETED, ListStatus.ON_HOLD, ListStatus.DROPPED)
    }

    companion object {
        private const val GENRE_PREFIX = "genre:"
        private const val STATUS_PREFIX = "status:"
        private const val DECADE_PREFIX = "decade:"
        /** Also the remote_lists keys of the other seasons. */
        const val SEASON_PREFIX = "season:"
        /** Also the remote_lists keys of the other users' decks. */
        const val USER_PREFIX = "user:"

        /**
         * Lists at the top of the categories screen, in this order. Lazy: when a data object is the
         * first thing used, the companion starts in the middle of that object's start, and a list
         * built right then would hold null in its place.
         */
        val specials: List<Category> by lazy { listOf(Popular, TopRated, Airing, Season, Upcoming, Movies, Suggested) }

        fun fromKey(key: String): Category? =
            specials.firstOrNull { it.key == key }
                ?: Unrated.takeIf { key == it.key }
                ?: key.takeIf { it.startsWith(GENRE_PREFIX) }?.removePrefix(GENRE_PREFIX)?.toIntOrNull()?.let(::OfGenre)
                ?: key.takeIf { it.startsWith(DECADE_PREFIX) }?.removePrefix(DECADE_PREFIX)?.toIntOrNull()
                    ?.takeIf(OfDecade::isValid)?.let(::OfDecade)
                ?: key.takeIf { it.startsWith(USER_PREFIX) }?.removePrefix(USER_PREFIX)
                    ?.takeIf(MalUserNames::isValid)?.let(::OfUser)
                ?: key.takeIf { it.startsWith(STATUS_PREFIX) }?.removePrefix(STATUS_PREFIX)
                    ?.let(ListStatus::fromApi)?.let(::OfStatus)
                ?: key.takeIf { it.startsWith(SEASON_PREFIX) }?.removePrefix(SEASON_PREFIX)?.split(':')
                    ?.takeIf { it.size == 2 }?.let { (year, season) -> year.toIntOrNull()?.let { OfSeason(it, season) } }
                    ?.takeIf { OfSeason.isValid(it.year, it.season) }
    }
}

/** MAL season of [year]: winter is January to March, and so on. */
data class AnimeSeason(val year: Int, val season: String) {
    companion object {
        fun current(today: LocalDate = LocalDate.now()): AnimeSeason = AnimeSeason(
            year = today.year,
            season = when (today.monthValue) {
                in 1..3 -> "winter"
                in 4..6 -> "spring"
                in 7..9 -> "summer"
                else -> "fall"
            },
        )
    }
}

enum class GenreGroup { GENRE, THEME, DEMOGRAPHIC }

/** MAL mixes genres, themes and demographics in one list: the ids tell them apart. */
object GenreGroups {
    private val demographics = setOf(15, 25, 27, 42, 43)
    private val genres = setOf(1, 2, 4, 5, 7, 8, 9, 10, 12, 14, 22, 24, 26, 28, 30, 36, 37, 41, 46, 47, 49)

    /** Hentai and Erotica: hidden unless adult content is enabled. */
    val adult = setOf(12, 49)

    fun of(genreId: Int): GenreGroup = when (genreId) {
        in demographics -> GenreGroup.DEMOGRAPHIC
        in genres -> GenreGroup.GENRE
        else -> GenreGroup.THEME
    }
}
