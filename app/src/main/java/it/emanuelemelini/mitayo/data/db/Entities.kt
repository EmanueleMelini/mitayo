package it.emanuelemelini.mitayo.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import it.emanuelemelini.mitayo.data.TitleChoice
import it.emanuelemelini.mitayo.data.TitleLanguage

/** Status of an entry in the user's MAL list; [apiValue] is also what the database stores. */
enum class ListStatus(val apiValue: String) {
    WATCHING("watching"),
    COMPLETED("completed"),
    ON_HOLD("on_hold"),
    DROPPED("dropped"),
    PLAN_TO_WATCH("plan_to_watch");

    companion object {
        fun fromApi(value: String?): ListStatus? = entries.firstOrNull { it.apiValue == value }
    }
}

/** An anime of the catalog (most popular first) or of a remote list (season, suggestions). */
@Entity(tableName = "anime", indices = [Index("popularity")])
data class Anime(
    @PrimaryKey val id: Int,
    val title: String,
    val titleEn: String?,
    /** Japanese title (kanji, kana), when MAL has it. */
    val titleJa: String?,
    val pictureUrl: String?,
    /** tv, movie, ova, ona, special, music… */
    val mediaType: String?,
    /** finished_airing, currently_airing, not_yet_aired. */
    val airingStatus: String?,
    /** 0 when unknown. */
    val episodes: Int,
    val mean: Double?,
    /** MAL popularity rank: 1 is the most popular. */
    val popularity: Int,
    val members: Int,
    val seasonYear: Int?,
    val season: String?,
    /** MAL "black" rating: shown only when adult content is enabled. */
    val nsfw: Boolean,
    /** Genre names for the card, in MAL order, comma separated. */
    val genreNames: String,
    /**
     * Last time MAL returned it in any list (epoch millis). The daily catalog pass removes the
     * anime it doesn't see any more: deleted from MAL, or out of the downloaded range.
     */
    val lastSeenAt: Long,
) {
    /** Can't be completed yet: a right swipe asks the episodes watched and sets it as watching. */
    val isAiring: Boolean get() = airingStatus == "currently_airing" || airingStatus == "not_yet_aired"

    /** null when MAL hasn't the title in that language. */
    fun titleIn(language: TitleLanguage): String? = when (language) {
        TitleLanguage.ROMAJI -> title
        TitleLanguage.ENGLISH -> titleEn
        TitleLanguage.JAPANESE -> titleJa
    }?.takeIf { it.isNotBlank() }

    /** The chosen title, or the romaji one when MAL hasn't it. */
    fun displayTitle(choice: TitleChoice): String = titleIn(choice.title) ?: title

    /** null with no subtitle chosen, when MAL hasn't it, or when it would repeat the title. */
    fun subtitle(choice: TitleChoice): String? =
        choice.subtitle?.let(::titleIn)?.takeIf { it != displayTitle(choice) }
}

/** MAL genres, themes and demographics share one id space. */
@Entity(tableName = "genres")
data class Genre(
    @PrimaryKey val id: Int,
    val name: String,
)

@Entity(tableName = "anime_genres", primaryKeys = ["animeId", "genreId"], indices = [Index("genreId")])
data class AnimeGenre(
    val animeId: Int,
    val genreId: Int,
)

/** Mirror of the user's MAL list, updated locally right away and synced from MAL. */
@Entity(tableName = "list_entries")
data class ListEntry(
    @PrimaryKey val animeId: Int,
    val status: ListStatus,
    val episodesWatched: Int,
    /** MAL score, 1 to 10; 0 = no score. */
    @ColumnInfo(defaultValue = "0") val score: Int = 0,
)

/** Swiped left: never shown again until the user restores the skipped anime. */
@Entity(tableName = "skipped")
data class SkippedAnime(
    @PrimaryKey val animeId: Int,
    val skippedAt: Long,
)

/** A change still to send to MAL. [status] null removes the anime from the list. */
@Entity(tableName = "outbox", indices = [Index("animeId")])
data class OutboxOp(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val animeId: Int,
    val status: ListStatus?,
    val episodesWatched: Int,
    val createdAt: Long,
    /** null: the score on MAL stays as it is; 0 clears it. */
    val score: Int? = null,
)

/** Order of an anime in a list that comes from MAL as is (current season, suggestions). */
@Entity(tableName = "remote_lists", primaryKeys = ["listKey", "animeId"])
data class RemoteListItem(
    val listKey: String,
    val animeId: Int,
    val position: Int,
)

/** A card of a deck: the anime and, if it's already in the user's list, its status there. */
data class DeckCard(
    @Embedded val anime: Anime,
    val listStatus: ListStatus?,
    val listEpisodes: Int?,
    val listScore: Int?,
)

/** A search result: the anime with its MAL status, and whether the user skipped it. */
data class CatalogMatch(
    @Embedded val card: DeckCard,
    val skipped: Boolean,
)

/** A genre with how many of its anime are still to swipe. */
data class GenreCount(
    val id: Int,
    val name: String,
    val remaining: Int,
)

/** A status of the user's list with how many of its anime a deck by status has. */
data class StatusCount(
    val status: ListStatus,
    val remaining: Int,
)

/** A decade (Category.OfDecade start) with how many of its anime are still to swipe. */
data class DecadeCount(
    val start: Int,
    val remaining: Int,
)
