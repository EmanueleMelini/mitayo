package it.emanuelemelini.mitayo.data.mal

import it.emanuelemelini.mitayo.data.auth.MalAuth
import it.emanuelemelini.mitayo.data.http.awaitText
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Official MAL API v2, always as the logged-in user. Requests go one at a time from the
 * repositories: MAL doesn't publish a rate limit and answers 403 when it sees too many.
 */
class MalApi(private val http: OkHttpClient, private val auth: MalAuth) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    suspend fun me(): MalUser = get("users/@me", mapOf("fields" to "picture"), MalUser.serializer())

    /**
     * Anyone's list with the anime themselves, 1000 per page; [userName] "@me" for the user's own.
     * MAL gives it only when the list is public (404: no such user).
     */
    suspend fun userAnimeList(userName: String, offset: Int): MalPage<MalUserAnime> = get(
        "users/$userName/animelist",
        mapOf(
            "fields" to "list_status,$CARD_FIELDS,average_episode_duration",
            "limit" to "$LIST_PAGE_SIZE",
            "offset" to "$offset",
            "nsfw" to "true",
        ),
        MalPage.serializer(MalUserAnime.serializer()),
    )

    /** Most popular first: the order of the catalog. */
    suspend fun popularity(offset: Int): MalPage<MalNode> = ranking("bypopularity", offset)

    /** The most awaited of the announced anime first. */
    suspend fun upcoming(offset: Int): MalPage<MalNode> = ranking("upcoming", offset)

    private suspend fun ranking(type: String, offset: Int): MalPage<MalNode> = get(
        "anime/ranking",
        mapOf(
            "ranking_type" to type,
            "limit" to "$RANKING_PAGE_SIZE",
            "offset" to "$offset",
            "fields" to CARD_FIELDS,
            "nsfw" to "true",
        ),
        MalPage.serializer(MalNode.serializer()),
    )

    suspend fun season(year: Int, season: String, offset: Int): MalPage<MalNode> = get(
        "anime/season/$year/$season",
        mapOf(
            "sort" to "anime_num_list_users",
            "limit" to "$RANKING_PAGE_SIZE",
            "offset" to "$offset",
            "fields" to CARD_FIELDS,
            "nsfw" to "true",
        ),
        MalPage.serializer(MalNode.serializer()),
    )

    /** MAL's own suggestions for the user (at most 100 per page). */
    suspend fun suggestions(offset: Int): MalPage<MalNode> = get(
        "anime/suggestions",
        mapOf("limit" to "$SUGGESTIONS_PAGE_SIZE", "offset" to "$offset", "fields" to CARD_FIELDS, "nsfw" to "true"),
        MalPage.serializer(MalNode.serializer()),
    )

    /** MAL's own search, for anime the local catalog doesn't have. MAL wants 3 to 64 characters. */
    suspend fun search(query: String): MalPage<MalNode> = get(
        "anime",
        mapOf("q" to query.take(64), "limit" to "$SEARCH_PAGE_SIZE", "fields" to CARD_FIELDS, "nsfw" to "true"),
        MalPage.serializer(MalNode.serializer()),
    )

    suspend fun details(animeId: Int): MalAnime = get(
        "anime/$animeId",
        mapOf("fields" to "$CARD_FIELDS,synopsis,studios,source,average_episode_duration,rating,related_anime,videos"),
        MalAnime.serializer(),
    )

    /** Creates or updates the entry in the user's list; [score] null leaves the score as it is. */
    suspend fun updateListStatus(animeId: Int, status: String, episodesWatched: Int, score: Int?) {
        val form = FormBody.Builder()
            .add("status", status)
            .add("num_watched_episodes", "$episodesWatched")
            .apply { if (score != null) add("score", "$score") }
            .build()
        send { token ->
            Request.Builder().url(url("anime/$animeId/my_list_status")).bearer(token).patch(form).build()
        }
    }

    /** Removes the entry from the user's list; one that is already gone (404) counts as done. */
    suspend fun deleteListStatus(animeId: Int) {
        try {
            send { token -> Request.Builder().url(url("anime/$animeId/my_list_status")).bearer(token).delete().build() }
        } catch (e: MalHttpException) {
            if (e.code != 404) throw e
        }
    }

    private suspend fun <T> get(path: String, params: Map<String, String>, serializer: KSerializer<T>): T {
        val body = send { token -> Request.Builder().url(url(path, params)).bearer(token).get().build() }
        return json.decodeFromString(serializer, body)
    }

    /** One retry with a fresh token when MAL rejects the current one. */
    private suspend fun send(build: (token: String) -> Request): String {
        val token = auth.accessToken()
        val (code, body) = http.newCall(build(token)).awaitText()
        if (code == 401) {
            val fresh = auth.accessToken(rejected = token)
            val (retryCode, retryBody) = http.newCall(build(fresh)).awaitText()
            if (retryCode == 401) throw NotLoggedInException()
            if (retryCode !in 200..299) throw MalHttpException(retryCode, retryBody)
            return retryBody
        }
        if (code !in 200..299) throw MalHttpException(code, body)
        return body
    }

    private fun url(path: String, params: Map<String, String> = emptyMap()) =
        "$BASE_URL/$path".toHttpUrl().newBuilder().apply {
            params.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()

    private fun Request.Builder.bearer(token: String) = header("Authorization", "Bearer $token")

    companion object {
        private const val BASE_URL = "https://api.myanimelist.net/v2"
        const val LIST_PAGE_SIZE = 1000
        const val RANKING_PAGE_SIZE = 500
        const val SEARCH_PAGE_SIZE = 30
        const val SUGGESTIONS_PAGE_SIZE = 100

        /** What a swipe card shows. */
        private const val CARD_FIELDS =
            "id,title,main_picture,alternative_titles,mean,popularity,num_list_users,media_type,status," +
                "genres,num_episodes,start_season,nsfw,broadcast"
    }
}
