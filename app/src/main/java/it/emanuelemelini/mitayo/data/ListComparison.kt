package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.mal.MalListStatus
import it.emanuelemelini.mitayo.data.mal.MalUserAnime
import kotlin.math.abs
import kotlin.math.sqrt

/** The totals of one list. */
data class ListStats(
    val entries: Int,
    val byStatus: Map<ListStatus, Int>,
    val episodes: Int,
    /** Episodes × their average length: close to MAL's "days", not the same figure. */
    val days: Double,
    /** Mean of the scores given (1 to 10); null without any. */
    val meanScore: Double?,
    val scored: Int,
    /** How many anime got each score: index 0 is score 1. */
    val scoreCounts: List<Int>,
)

/** An anime in both lists. */
data class SharedAnime(val anime: MalAnime, val a: MalListStatus, val b: MalListStatus) {
    /** A's score minus B's, when both gave one. */
    val difference: Int? get() = if (a.score > 0 && b.score > 0) a.score - b.score else null
}

/** Two lists side by side, like MAL's "shared anime" page, computed from the API. */
data class Comparison(
    val a: ListStats,
    val b: ListStats,
    val shared: List<SharedAnime>,
    val onlyA: List<MalUserAnime>,
    val onlyB: List<MalUserAnime>,
    /** Means over the shared anime both scored. */
    val sharedMeanA: Double?,
    val sharedMeanB: Double?,
    /** Mean of the absolute score differences on the shared anime both scored. */
    val meanDifference: Double?,
    /** Correlation of the scores on the shared anime both scored, -100 to 100; null under 2 or without variation. */
    val affinity: Double?,
)

object ListComparison {

    fun compare(a: List<MalUserAnime>, b: List<MalUserAnime>): Comparison {
        val byIdB = b.associateBy { it.node.id }
        val idsA = a.mapTo(HashSet()) { it.node.id }
        val shared = a.mapNotNull { entry -> byIdB[entry.node.id]?.let { SharedAnime(entry.node, entry.listStatus, it.listStatus) } }
            .sortedBy { it.anime.title.lowercase() }
        val scoredPairs = shared.filter { it.difference != null }.map { it.a.score.toDouble() to it.b.score.toDouble() }
        return Comparison(
            a = stats(a),
            b = stats(b),
            shared = shared,
            onlyA = a.filter { it.node.id !in byIdB }.sortedBy { it.node.title.lowercase() },
            onlyB = b.filter { it.node.id !in idsA }.sortedBy { it.node.title.lowercase() },
            sharedMeanA = scoredPairs.map { it.first }.averageOrNull(),
            sharedMeanB = scoredPairs.map { it.second }.averageOrNull(),
            meanDifference = scoredPairs.map { abs(it.first - it.second) }.averageOrNull(),
            affinity = affinity(scoredPairs),
        )
    }

    fun stats(list: List<MalUserAnime>): ListStats {
        val scores = list.map { it.listStatus.score }.filter { it in 1..10 }
        return ListStats(
            entries = list.size,
            byStatus = ListStatus.entries.associateWith { status -> list.count { it.listStatus.status == status.apiValue } },
            episodes = list.sumOf { it.listStatus.episodesWatched },
            days = list.sumOf { it.listStatus.episodesWatched.toDouble() * (it.node.averageEpisodeDuration ?: 0) } / SECONDS_PER_DAY,
            meanScore = scores.map { it.toDouble() }.averageOrNull(),
            scored = scores.size,
            scoreCounts = (1..10).map { score -> scores.count { it == score } },
        )
    }

    /** Pearson correlation as a percentage, like MAL's old "affinity". */
    internal fun affinity(pairs: List<Pair<Double, Double>>): Double? {
        if (pairs.size < 2) return null
        val meanA = pairs.map { it.first }.average()
        val meanB = pairs.map { it.second }.average()
        var covariance = 0.0
        var varianceA = 0.0
        var varianceB = 0.0
        pairs.forEach { (x, y) ->
            covariance += (x - meanA) * (y - meanB)
            varianceA += (x - meanA) * (x - meanA)
            varianceB += (y - meanB) * (y - meanB)
        }
        if (varianceA == 0.0 || varianceB == 0.0) return null
        return 100 * covariance / sqrt(varianceA * varianceB)
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private const val SECONDS_PER_DAY = 86_400.0
}
