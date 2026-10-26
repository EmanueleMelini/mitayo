package it.emanuelemelini.mitayo.data

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery

/**
 * SQL of a deck. The decks show only the anime that aren't in the user's MAL list at all (and
 * weren't skipped); the decks by status show the anime of the list with that status.
 */
internal object DeckQuery {

    fun cards(category: Category, showNsfw: Boolean, limit: Int): SupportSQLiteQuery =
        build(category, showNsfw, "a.*, l.status AS listStatus, l.episodesWatched AS listEpisodes, l.score AS listScore", limit)

    fun count(category: Category, showNsfw: Boolean): SupportSQLiteQuery =
        build(category, showNsfw, "COUNT(*)", limit = null)

    private fun build(category: Category, showNsfw: Boolean, columns: String, limit: Int?): SupportSQLiteQuery {
        val args = mutableListOf<Any>()
        val sql = StringBuilder("SELECT $columns FROM anime a LEFT JOIN list_entries l ON l.animeId = a.id")
        if (category.isRemote) {
            sql.append(" JOIN remote_lists r ON r.animeId = a.id AND r.listKey = ?")
            args += category.key
        }
        if (category is Category.OfStatus) {
            sql.append(" WHERE l.status = ?")
            args += category.status.apiValue
        } else {
            sql.append(" WHERE l.status IS NULL")
        }
        sql.append(" AND a.id NOT IN (SELECT animeId FROM skipped)")
        if (!showNsfw) sql.append(" AND a.nsfw = 0")
        when (category) {
            Category.Popular, Category.Season, Category.Suggested, is Category.OfUser, is Category.OfStatus -> Unit
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
        if (limit != null) {
            val order = when (category) {
                Category.TopRated -> "a.mean DESC"
                Category.Season, Category.Suggested, is Category.OfUser -> "r.position"
                // Most popular first: the ones the user most likely watched
                else -> "a.popularity"
            }
            sql.append(" ORDER BY $order, a.id LIMIT ?")
            args += limit
        }
        return SimpleSQLiteQuery(sql.toString(), args.toTypedArray())
    }
}
