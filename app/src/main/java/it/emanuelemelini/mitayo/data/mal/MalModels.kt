package it.emanuelemelini.mitayo.data.mal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** MAL API v2 response shapes: only the fields Mitayo asks for. */

@Serializable
data class MalPage<T>(
    val data: List<T> = emptyList(),
    val paging: MalPaging = MalPaging(),
)

@Serializable
data class MalPaging(val next: String? = null)

@Serializable
data class MalNode(val node: MalAnime)

/** An entry of anyone's list with the anime itself: comparisons and other users' decks. */
@Serializable
data class MalUserAnime(
    val node: MalAnime,
    @SerialName("list_status") val listStatus: MalListStatus,
)

@Serializable
data class MalListStatus(
    val status: String? = null,
    @SerialName("num_episodes_watched") val episodesWatched: Int = 0,
    val score: Int = 0,
)

@Serializable
data class MalAnime(
    val id: Int,
    val title: String,
    @SerialName("main_picture") val mainPicture: MalPicture? = null,
    @SerialName("alternative_titles") val alternativeTitles: MalAlternativeTitles? = null,
    val mean: Double? = null,
    val popularity: Int? = null,
    @SerialName("num_list_users") val numListUsers: Int? = null,
    @SerialName("media_type") val mediaType: String? = null,
    /** finished_airing, currently_airing, not_yet_aired. */
    val status: String? = null,
    val genres: List<MalGenre> = emptyList(),
    /** 0 when MAL doesn't know it yet (e.g. still airing). */
    @SerialName("num_episodes") val numEpisodes: Int = 0,
    @SerialName("start_season") val startSeason: MalSeason? = null,
    /** white, gray or black (adult). */
    val nsfw: String? = null,
    /** When it airs each week in Japan, for the shows that have a weekly slot. */
    val broadcast: MalBroadcast? = null,
    // Details only
    val synopsis: String? = null,
    val studios: List<MalStudio> = emptyList(),
    val source: String? = null,
    @SerialName("average_episode_duration") val averageEpisodeDuration: Int? = null,
    val rating: String? = null,
    @SerialName("related_anime") val relatedAnime: List<MalRelated> = emptyList(),
    /** Trailers and promotional videos (YouTube links), as MAL lists them. */
    val videos: List<MalVideo> = emptyList(),
)

@Serializable
data class MalVideo(
    val title: String? = null,
    val url: String,
    val thumbnail: String? = null,
)

/** Another anime of the same story (sequel, prequel...), as the details list it. */
@Serializable
data class MalRelated(
    val node: MalRelatedNode,
    /** sequel, prequel, side_story, parent_story, alternative_version... */
    @SerialName("relation_type") val relationType: String,
)

@Serializable
data class MalRelatedNode(
    val id: Int,
    val title: String,
    @SerialName("main_picture") val mainPicture: MalPicture? = null,
)

@Serializable
data class MalPicture(val medium: String? = null, val large: String? = null)

@Serializable
data class MalAlternativeTitles(val en: String? = null, val ja: String? = null)

@Serializable
data class MalGenre(val id: Int, val name: String)

@Serializable
data class MalSeason(val year: Int, val season: String)

/** [dayOfTheWeek]: monday... sunday; [startTime]: "HH:mm" in Japan (JST). */
@Serializable
data class MalBroadcast(
    @SerialName("day_of_the_week") val dayOfTheWeek: String? = null,
    @SerialName("start_time") val startTime: String? = null,
)

@Serializable
data class MalStudio(val id: Int, val name: String)

@Serializable
data class MalUser(val id: Int, val name: String, val picture: String? = null)
