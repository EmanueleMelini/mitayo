package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.mal.MalUserAnime
import kotlin.math.sqrt

/**
 * Affinity by tastes (v2): each list becomes a profile of how much its user likes every attribute
 * of the anime they scored (genres, themes, demographic, studios, source, age rating, type,
 * decade), from their own scores only, never MAL's mean. Then the two profiles are compared, so
 * no anime in common is needed. MAL's API has no producers: only the studios.
 */
object TasteAffinity {

    /** Kinds of attribute, with their share of the result. */
    enum class Group(val weight: Double) {
        GENRE(0.30),
        THEME(0.20),
        DEMOGRAPHIC(0.10),
        STUDIO(0.15),
        SOURCE(0.10),
        RATING(0.05),
        TYPE(0.05),
        DECADE(0.05),
    }

    data class Attribute(val group: Group, val value: String)

    /**
     * -100 to 100, like the affinity by shared scores. null when one of the two has fewer than
     * [MIN_SCORED] scores, or gave the same score to everything.
     */
    fun affinity(a: List<MalUserAnime>, b: List<MalUserAnime>): Double? {
        val tastesA = profile(a) ?: return null
        val tastesB = profile(b) ?: return null
        var sum = 0.0
        var weights = 0.0
        Group.entries.forEach { group ->
            // A group one of the two says nothing about doesn't count, in either direction
            val similarity = cosine(tastesA.filterKeys { it.group == group }, tastesB.filterKeys { it.group == group })
                ?: return@forEach
            sum += group.weight * similarity
            weights += group.weight
        }
        return if (weights > 0) 100 * sum / weights else null
    }

    /**
     * How much the user likes each attribute: the mean of (score − their mean score) over the
     * anime that have it, pulled towards 0 when they are few ([PRIOR]). null under [MIN_SCORED] scores.
     */
    internal fun profile(list: List<MalUserAnime>): Map<Attribute, Double>? {
        val scored = list.filter { it.listStatus.score in 1..10 }
        if (scored.size < MIN_SCORED) return null
        val mean = scored.map { it.listStatus.score }.average()
        val sums = HashMap<Attribute, Double>()
        val counts = HashMap<Attribute, Int>()
        scored.forEach { entry ->
            val weight = entry.listStatus.score - mean
            attributes(entry.node).forEach { attribute ->
                sums[attribute] = (sums[attribute] ?: 0.0) + weight
                counts[attribute] = (counts[attribute] ?: 0) + 1
            }
        }
        return sums.mapValues { (attribute, sum) -> sum / (counts.getValue(attribute) + PRIOR) }
    }

    /** MAL gives genres, themes and demographics in one list: [GenreGroups] tells them apart. */
    internal fun attributes(anime: MalAnime): Set<Attribute> = buildSet {
        anime.genres.forEach { genre ->
            val group = when (GenreGroups.of(genre.id)) {
                GenreGroup.GENRE -> Group.GENRE
                GenreGroup.THEME -> Group.THEME
                GenreGroup.DEMOGRAPHIC -> Group.DEMOGRAPHIC
            }
            add(Attribute(group, genre.id.toString()))
        }
        anime.studios.forEach { add(Attribute(Group.STUDIO, it.id.toString())) }
        anime.source?.known()?.let { add(Attribute(Group.SOURCE, it)) }
        anime.rating?.known()?.let { add(Attribute(Group.RATING, it)) }
        anime.mediaType?.known()?.let { add(Attribute(Group.TYPE, it)) }
        anime.startSeason?.let { add(Attribute(Group.DECADE, (it.year / 10 * 10).toString())) }
    }

    /** Cosine similarity, -1 to 1; null when one of the two is all zero (or empty). */
    private fun cosine(a: Map<Attribute, Double>, b: Map<Attribute, Double>): Double? {
        val normA = sqrt(a.values.sumOf { it * it })
        val normB = sqrt(b.values.sumOf { it * it })
        if (normA == 0.0 || normB == 0.0) return null
        val dot = a.entries.sumOf { (attribute, value) -> value * (b[attribute] ?: 0.0) }
        return dot / (normA * normB)
    }

    private fun String.known(): String? = takeIf { it.isNotBlank() && it != UNKNOWN }

    /** Fewer scores than this say too little about someone's tastes. */
    const val MIN_SCORED = 10
    /** As many anime more at the user's own mean: one anime alone doesn't make a taste. */
    private const val PRIOR = 3.0
    private const val UNKNOWN = "unknown"
}
