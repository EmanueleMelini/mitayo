package it.emanuelemelini.mitayo.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * SQL of a deck. The decks show only the anime that aren't in the user's MAL list at all (and
 * weren't skipped); the decks by status show the anime of the list with that status, the deck to
 * score the ones seen or started without a score.
 */
internal object DeckQuery {

    /** [shuffleSeed]: a random order, the same for the whole visit (null: the deck's own order). */
    fun cards(
        category: Category,
        showNsfw: Boolean,
        limit: Int,
        filters: DeckFilters = DeckFilters(),
        shuffleSeed: Long? = null,
    ): SupportSQLiteQuery = build(
        category,
        showNsfw,
        filters,
        "a.*, l.status AS listStatus, l.episodesWatched AS listEpisodes, l.score AS listScore",
        limit,
        shuffleSeed,
    )

    fun count(category: Category, showNsfw: Boolean, filters: DeckFilters = DeckFilters()): SupportSQLiteQuery =
        build(category, showNsfw, filters, "COUNT(*)", limit = null, shuffleSeed = null)

    private fun build(
        category: Category,
        showNsfw: Boolean,
        filters: DeckFilters,
        columns: String,
        limit: Int?,
        shuffleSeed: Long?,
    ): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val sql = StringBuilder("SELECT $columns FROM anime a LEFT JOIN list_entries l ON l.animeId = a.id")
        if (category.isRemote) {
            sql.append(" JOIN remote_lists r ON r.animeId = a.id AND r.listKey = ?")
            args += category.key
        }
        if (category is Category.OfStatus) {
            sql.append(" WHERE l.status = ?")
            args += category.status.apiValue
        } else if (category == Category.Unrated) {
            sql.append(" WHERE l.score = 0 AND l.status IN (${Category.Unrated.statuses.joinToString { "?" }})")
            args.addAll(Category.Unrated.statuses.map { it.apiValue })
        } else {
            sql.append(" WHERE l.status IS NULL")
        }
        sql.append(" AND a.id NOT IN (SELECT animeId FROM skipped)")
        if (!showNsfw) sql.append(" AND a.nsfw = 0")
        when (category) {
            Category.Popular, Category.Season, Category.Upcoming, Category.Suggested,
            is Category.OfSeason, is Category.OfUser, is Category.OfStatus, Category.Unrated -> Unit
            Category.TopRated -> sql.append(" AND a.mean IS NOT NULL")
            Category.Airing -> sql.append(" AND a.airingStatus = 'currently_airing'")
            Category.Movies -> sql.append(" AND a.mediaType = 'movie'")
            is Category.OfGenre -> {
                sql.append(" AND a.id IN (SELECT animeId FROM anime_genres WHERE genreId = ?)")
                args += category.genreId
            }
            is Category.OfDecade -> {
                sql.append(" AND a.seasonYear BETWEEN ? AND ?")
                args += category.years.first
                args += category.years.last
            }
        }
        if (filters.kinds.isNotEmpty()) {
            val types = filters.kinds.flatMap { it.malTypes }
            sql.append(" AND a.mediaType IN (${types.joinToString { "?" }})")
            args.addAll(types)
        }
        filters.minScore?.let {
            sql.append(" AND a.mean >= ?")
            args += it
        }
        filters.years?.let {
            sql.append(" AND a.seasonYear BETWEEN ? AND ?")
            args += it.first
            args += it.last
        }
        if (limit != null) {
            val order = when {
                // A hash of the id: random, but the same at every query of the visit
                shuffleSeed != null -> {
                    args += shuffleSeed
                    "((a.id + ?) * 1103515245 + 12345) % 2147483647"
                }
                category == Category.TopRated -> "a.mean DESC"
                category.isRemote -> "r.position"
                // Most popular first: the ones the user most likely watched
                else -> "a.popularity"
            }
            sql.append(" ORDER BY $order, a.id LIMIT ?")
            args += limit
        }
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
