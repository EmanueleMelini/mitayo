package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.mal.MalListStatus
import it.emanuelemelini.mitayo.data.mal.MalUserAnime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListComparisonTest {

    private fun entry(id: Int, status: String, episodes: Int, score: Int, duration: Int = 1440) = MalUserAnime(
        node = MalAnime(id = id, title = "Anime $id", averageEpisodeDuration = duration),
        listStatus = MalListStatus(status = status, episodesWatched = episodes, score = score),
    )

    private val a = listOf(
        entry(1, "completed", 12, 8),
        entry(2, "completed", 24, 6),
        entry(3, "watching", 5, 0),
        entry(4, "plan_to_watch", 0, 0),
    )
    private val b = listOf(
        entry(1, "completed", 12, 9),
        entry(2, "dropped", 3, 4),
        entry(5, "completed", 1, 10, duration = 7200),
    )

    @Test
    fun statsCountStatusesEpisodesAndScores() {
        val stats = ListComparison.stats(a)
        assertEquals(4, stats.entries)
        assertEquals(2, stats.byStatus[ListStatus.COMPLETED])
        assertEquals(1, stats.byStatus[ListStatus.PLAN_TO_WATCH])
        assertEquals(41, stats.episodes)
        // 41 episodes of 24 minutes
        assertEquals(41 * 1440 / 86_400.0, stats.days, 1e-9)
        assertEquals(7.0, stats.meanScore!!, 1e-9)
        assertEquals(2, stats.scored)
        assertEquals(1, stats.scoreCounts[7])
        assertEquals(1, stats.scoreCounts[5])
    }

    @Test
    fun sharedAndOnlyListsSplitByAnime() {
        val comparison = ListComparison.compare(a, b)
        assertEquals(listOf(1, 2), comparison.shared.map { it.anime.id })
        assertEquals(listOf(3, 4), comparison.onlyA.map { it.node.id })
        assertEquals(listOf(5), comparison.onlyB.map { it.node.id })
        assertEquals(-1, comparison.shared[0].difference)
        assertEquals(2, comparison.shared[1].difference)
        assertEquals(7.0, comparison.sharedMeanA!!, 1e-9)
        assertEquals(6.5, comparison.sharedMeanB!!, 1e-9)
        assertEquals(1.5, comparison.meanDifference!!, 1e-9)
    }

    @Test
    fun affinityIsTheCorrelationOfTheScores() {
        assertEquals(100.0, ListComparison.affinity(listOf(1.0 to 2.0, 5.0 to 6.0, 9.0 to 10.0))!!, 1e-9)
        assertEquals(-100.0, ListComparison.affinity(listOf(1.0 to 10.0, 5.0 to 6.0, 9.0 to 2.0))!!, 1e-9)
        assertNull(ListComparison.affinity(listOf(7.0 to 8.0)))
        // Every score the same: no correlation to measure
        assertNull(ListComparison.affinity(listOf(7.0 to 8.0, 7.0 to 3.0)))
    }

    @Test
    fun unscoredSharedAnimeHaveNoDifference() {
        val comparison = ListComparison.compare(listOf(entry(1, "watching", 1, 0)), listOf(entry(1, "completed", 12, 9)))
        assertNull(comparison.shared.single().difference)
        assertNull(comparison.affinity)
        assertNull(comparison.meanDifference)
    }

    @Test
    fun userNamesFollowMalRules() {
        assert(MalUserNames.isValid("LeleMims"))
        assert(MalUserNames.isValid("a_b-9"))
        assert(!MalUserNames.isValid("x"))
        assert(!MalUserNames.isValid("name with space"))
        assert(!MalUserNames.isValid("../users"))
    }
}
