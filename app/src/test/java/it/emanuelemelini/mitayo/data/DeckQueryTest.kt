package it.emanuelemelini.mitayo.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckQueryTest {

    @Test
    fun filtersBecomeConditionsWithTheirArguments() {
        val filters = DeckFilters(kinds = setOf(MediaKind.TV, MediaKind.SPECIAL), minScore = 7, years = 2000..2009)
        val query = DeckQuery.cards(Category.Popular, showNsfw = true, limit = 30, filters = filters)
        val sql = query.sql
        assertTrue(sql, sql.contains("a.mediaType IN (?, ?, ?)"))
        assertTrue(sql, sql.contains("a.mean >= ?"))
        assertTrue(sql, sql.contains("a.seasonYear BETWEEN ? AND ?"))
        assertTrue(sql, sql.endsWith("ORDER BY a.popularity, a.id LIMIT ?"))
        // Media types, score, years, then the limit
        assertEquals(7, query.argCount)
    }

    @Test
    fun shuffleReplacesTheDeckOrder() {
        val query = DeckQuery.cards(Category.TopRated, showNsfw = false, limit = 30, shuffleSeed = 42)
        assertTrue(query.sql, query.sql.contains("ORDER BY ((a.id + ?) * 1103515245 + 12345) % 2147483647, a.id LIMIT ?"))
        assertEquals(2, query.argCount)
    }

    @Test
    fun remoteDecksKeepTheirOrder() {
        val query = DeckQuery.cards(Category.Upcoming, showNsfw = false, limit = 30)
        assertTrue(query.sql, query.sql.contains("JOIN remote_lists r ON r.animeId = a.id AND r.listKey = ?"))
        assertTrue(query.sql, query.sql.endsWith("ORDER BY r.position, a.id LIMIT ?"))
    }

    @Test
    fun countIgnoresTheShuffle() {
        val query = DeckQuery.count(Category.OfSeason(2019, "spring"), showNsfw = true, filters = DeckFilters(minScore = 8))
        assertTrue(query.sql, query.sql.startsWith("SELECT COUNT(*)"))
        assertEquals(2, query.argCount)
    }
}
