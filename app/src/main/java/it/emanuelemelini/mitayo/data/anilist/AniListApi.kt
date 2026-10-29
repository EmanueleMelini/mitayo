package it.emanuelemelini.mitayo.data.anilist

import it.emanuelemelini.mitayo.data.http.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** AniList refused the token: expired (it lasts a year) or revoked. The link must be made again. */
class AniListUnauthorizedException : Exception("AniList token not valid")

/** AniList answered with an error status ([code]); the message never has the body, it may end up in a crash report. */
class AniListHttpException(val code: Int) : Exception("AniList HTTP $code")

/**
 * The AniList GraphQL API, one request at a time and no more than [MIN_INTERVAL_MS] apart: AniList
 * allows 30 requests a minute for now (90 normally) and stops for a minute whoever goes over.
 */
class AniListApi(private val http: OkHttpClient, private val token: suspend () -> String?) {

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var lastRequestAt = 0L

    /** The user of [accessToken], or of the saved token when null. */
    suspend fun viewer(accessToken: String? = null): AniListUser =
        query(VIEWER, JsonObject(emptyMap()), ViewerData.serializer(), accessToken).viewer

    /** The whole anime list of [userId], [LIST_CHUNK] entries a request; an entry in several lists comes once. */
    suspend fun animeList(userId: Int): List<AniListListEntry> {
        val entries = LinkedHashMap<Int, AniListListEntry>()
        var chunk = 1
        while (true) {
            val variables = buildJsonObject {
                put("userId", userId)
                put("chunk", chunk)
                put("perChunk", LIST_CHUNK)
            }
            val collection = query(LIST, variables, ListCollectionData.serializer()).collection ?: break
            collection.lists.flatMap { it.entries }.forEach { entries.putIfAbsent(it.id, it) }
            if (!collection.hasNextChunk) break
            chunk++
        }
        return entries.values.toList()
    }

    /** The AniList anime of these MAL ids, [MEDIA_PAGE] at most; the ids AniList hasn't are missing. */
    suspend fun mediaByMalIds(malIds: List<Int>): List<AniListMedia> {
        val variables = buildJsonObject { putJsonArray("ids") { malIds.forEach { add(JsonPrimitive(it)) } } }
        return query(MEDIA_BY_MAL, variables, MediaPageData.serializer()).page.media
    }

    /** Adds or changes the anime in the list; [scoreRaw] null keeps the score there. */
    suspend fun save(mediaId: Int, status: AniListStatus, progress: Int, scoreRaw: Int?): SavedEntry {
        val variables = buildJsonObject {
            put("mediaId", mediaId)
            put("status", status.name)
            put("progress", progress)
            // A variable left out leaves the argument out, so the score stays
            if (scoreRaw != null) put("scoreRaw", scoreRaw)
        }
        return query(SAVE, variables, SaveData.serializer()).saved
    }

    /** Removes the list entry [entryId]; one already gone is fine. */
    suspend fun delete(entryId: Int) {
        try {
            query(DELETE, buildJsonObject { put("id", entryId) }, JsonObject.serializer())
        } catch (e: AniListHttpException) {
            if (e.code != 404) throw e
        }
    }

    private suspend fun <T> query(
        query: String,
        variables: JsonObject,
        serializer: KSerializer<T>,
        accessToken: String? = null,
    ): T = mutex.withLock {
        val bearer = accessToken ?: token()
        val body = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString().toRequestBody(JSON_TYPE)
        val request = Request.Builder()
            .url(URL)
            .header("Accept", "application/json")
            .apply { if (bearer != null) header("Authorization", "Bearer $bearer") }
            .post(body)
            .build()
        for (attempt in 1..RATE_LIMIT_TRIES) {
            val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            lastRequestAt = System.currentTimeMillis()
            val (code, text, retryAfter) = http.newCall(request).await().let { response ->
                withContext(Dispatchers.IO) {
                    response.use { Triple(it.code, it.body.string(), it.header("Retry-After")?.toLongOrNull()) }
                }
            }
            if (code == 429) {
                // Stopped for a minute: wait and try again, while holding the queue
                delay((retryAfter ?: 60L).coerceIn(1L, 120L) * 1000L)
                continue
            }
            val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
            val errors = root?.get("errors") as? JsonArray
            if (bearer != null && (code == 401 || (code == 400 && errors?.toString()?.contains("Invalid token") == true))) {
                throw AniListUnauthorizedException()
            }
            val data = root?.get("data")
            // Any error fails the request: a field left null by it doesn't fit the models anyway
            if (code !in 200..299 || !errors.isNullOrEmpty() || data == null || data is JsonNull) {
                throw AniListHttpException(errorStatus(errors) ?: code)
            }
            return@withLock try {
                json.decodeFromJsonElement(serializer, data)
            } catch (_: SerializationException) {
                throw AniListHttpException(code)
            }
        }
        throw AniListHttpException(429)
    }

    /** The status of the first GraphQL error, when AniList gives one (404, 400…). */
    private fun errorStatus(errors: JsonArray?): Int? =
        (errors?.firstOrNull() as? JsonObject)?.get("status")?.jsonPrimitive?.intOrNull

    companion object {
        /** 30 a minute, with some margin. */
        const val MIN_INTERVAL_MS = 2_100L
        const val MEDIA_PAGE = 50
        private const val LIST_CHUNK = 500
        private const val RATE_LIMIT_TRIES = 3
        private const val URL = "https://graphql.anilist.co"
        private val JSON_TYPE = "application/json".toMediaType()

        private val VIEWER = """
            query { Viewer { id name avatar { medium } options { displayAdultContent } mediaListOptions { scoreFormat } } }
        """.trimIndent()

        private val LIST = """
            query (${'$'}userId: Int, ${'$'}chunk: Int, ${'$'}perChunk: Int) {
              MediaListCollection(userId: ${'$'}userId, type: ANIME, chunk: ${'$'}chunk, perChunk: ${'$'}perChunk) {
                hasNextChunk
                lists { entries { id status progress scoreRaw: score(format: POINT_100) media { id idMal title { romaji } } } }
              }
            }
        """.trimIndent()

        private val MEDIA_BY_MAL = """
            query (${'$'}ids: [Int]) { Page(perPage: $MEDIA_PAGE) { media(idMal_in: ${'$'}ids, type: ANIME) { id idMal } } }
        """.trimIndent()

        private val SAVE = """
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus, ${'$'}progress: Int, ${'$'}scoreRaw: Int) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status, progress: ${'$'}progress, scoreRaw: ${'$'}scoreRaw) {
                id status progress scoreRaw: score(format: POINT_100)
              }
            }
        """.trimIndent()

        private val DELETE = """
            mutation (${'$'}id: Int) { DeleteMediaListEntry(id: ${'$'}id) { deleted } }
        """.trimIndent()
    }
}
