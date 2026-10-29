package it.emanuelemelini.mitayo.data.anilist

import it.emanuelemelini.mitayo.data.db.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AniListMappingTest {

    @Test
    fun everyMalStatusGoesAndComesBack() {
        ListStatus.entries.forEach { assertEquals(it, AniListStatus.fromMal(it).toMal()) }
        assertEquals(ListStatus.COMPLETED, AniListStatus.REPEATING.toMal())
    }

    @Test
    fun scoresConvertBothWays() {
        assertEquals(80, Scores.toAniList(8))
        assertEquals(0, Scores.toAniList(0))
        assertEquals(9, Scores.toMal(85))
        assertEquals(8, Scores.toMal(84))
        assertEquals(1, Scores.toMal(3))
        assertEquals(0, Scores.toMal(0))
        (0..10).forEach { assertEquals(it, Scores.toMal(Scores.toAniList(it))) }
    }

    @Test
    fun scoresMatchOnTheCoarserScale() {
        assertTrue(Scores.match(9, 85, ScoreFormat.POINT_100))
        assertFalse(Scores.match(8, 85, ScoreFormat.POINT_100))
        assertTrue(Scores.match(9, 85, ScoreFormat.POINT_10_DECIMAL))
        assertTrue(Scores.match(7, 80, ScoreFormat.POINT_5))
        assertTrue(Scores.match(8, 80, ScoreFormat.POINT_5))
        assertFalse(Scores.match(9, 80, ScoreFormat.POINT_5))
        assertTrue(Scores.match(7, 85, ScoreFormat.POINT_3))
        assertFalse(Scores.match(5, 85, ScoreFormat.POINT_3))
        assertTrue(Scores.match(0, 0, ScoreFormat.POINT_10))
        assertFalse(Scores.match(0, 70, ScoreFormat.POINT_10))
    }

    /** What AniList's API answered for these scores, in stars and smileys. */
    @Test
    fun starsAndSmileysAreAniListOnes() {
        val answered = mapOf(35 to (2 to 1), 40 to (2 to 2), 50 to (3 to 2), 60 to (3 to 2), 70 to (4 to 3), 80 to (4 to 3), 85 to (4 to 3), 90 to (5 to 3), 100 to (5 to 3))
        answered.forEach { (raw, shown) ->
            assertEquals("stars of $raw", shown.first, Scores.stars(raw))
            assertEquals("smiley of $raw", shown.second, Scores.smiley(raw))
        }
    }

    @Test
    fun theTokenComesFromTheFragment() {
        assertEquals(
            AniListLogin.Redirect.Token("abc.def", 31536000),
            AniListLogin.parseRedirect("access_token=abc.def&token_type=Bearer&expires_in=31536000", null),
        )
        assertEquals(AniListLogin.Redirect.Cancelled, AniListLogin.parseRedirect(null, "access_denied"))
        assertEquals(AniListLogin.Redirect.Failed, AniListLogin.parseRedirect("token_type=Bearer", null))
    }
}
