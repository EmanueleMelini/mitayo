package it.emanuelemelini.mitayo.data

/** Narrows a deck while it's open: kinds of anime, lowest MAL score, years. Empty: everything. */
data class DeckFilters(
    val kinds: Set<MediaKind> = emptySet(),
    /** Lowest MAL mean score; null: any, also without a score. */
    val minScore: Int? = null,
    /** Years the anime started in; null: any. */
    val years: IntRange? = null,
) {
    val active: Boolean get() = kinds.isNotEmpty() || minScore != null || years != null
}

/** The kinds of anime the filters offer, with the MAL media types each one takes. */
enum class MediaKind(val malTypes: List<String>) {
    TV(listOf("tv")),
    MOVIE(listOf("movie")),
    OVA(listOf("ova")),
    ONA(listOf("ona")),
    SPECIAL(listOf("special", "tv_special")),
    MUSIC(listOf("music")),
}
