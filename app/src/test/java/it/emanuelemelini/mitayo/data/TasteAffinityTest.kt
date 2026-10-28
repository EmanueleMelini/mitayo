package it.emanuelemelini.mitayo.data

import it.emanuelemelini.mitayo.data.TasteAffinity.Attribute
import it.emanuelemelini.mitayo.data.TasteAffinity.Group
import it.emanuelemelini.mitayo.data.mal.MalAnime
import it.emanuelemelini.mitayo.data.mal.MalGenre
import it.emanuelemelini.mitayo.data.mal.MalListStatus
import it.emanuelemelini.mitayo.data.mal.MalSeason
import it.emanuelemelini.mitayo.data.mal.MalStudio
import it.emanuelemelini.mitayo.data.mal.MalUserAnime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteAffinityTest {

    /** Action, military, shounen, by one studio, from a manga. */
    private fun action(id: Int, score: Int) = entry(id, score, listOf(1, 38, 27), studio = 858, source = "manga", year = 2013)

    /** Romance, school, shoujo, by another studio, from a light novel. */
    private fun romance(id: Int, score: Int) = entry(id, score, listOf(22, 23, 25), studio = 1, source = "light_novel", year = 2008)

    private fun entry(id: Int, score: Int, genres: List<Int>, studio: Int, source: String, year: Int) = MalUserAnime(
        node = MalAnime(
            id = id,
            title = "Anime $id",
            genres = genres.map { MalGenre(it, "Genre $it") },
            studios = listOf(MalStudio(studio, "Studio $studio")),
            source = source,
            mediaType = "tv",
            rating = "pg_13",
            startSeason = MalSeason(year, "spring"),
        ),
        listStatus = MalListStatus(status = "completed", episodesWatched = 12, score = score),
    )

    /** Five of each, the ids from [firstId]: different anime for each user. */
    private fun list(firstId: Int, actionScore: Int, romanceScore: Int) =
        (0 until 5).map { action(firstId + it, actionScore) } + (5 until 10).map { romance(firstId + it, romanceScore) }

    @Test
    fun sameTastesMatchWithoutSharedAnime() {
        // B gives a point less to everything: the tastes are the same
        val affinity = TasteAffinity.affinity(list(100, 9, 5), list(200, 8, 4))
        assertEquals(100.0, affinity!!, 1e-9)
    }

    @Test
    fun oppositeTastesGoNegative() {
        val affinity = TasteAffinity.affinity(list(100, 9, 5), list(200, 5, 9))
        assertEquals(-100.0, affinity!!, 1e-9)
    }

    @Test
    fun partlySharedTastesFallInBetween() {
        // B likes the action anime as much as A, and is lukewarm on romance only by a little
        val b = (0 until 5).map { action(200 + it, 9) } + (5 until 10).map { romance(200 + it, 8) } + romance(300, 4)
        val affinity = TasteAffinity.affinity(list(100, 9, 5), b)!!
        assertTrue("$affinity", affinity > 0 && affinity < 100)
    }

    @Test
    fun tooFewScoresGiveNoAffinity() {
        val few = list(200, 8, 4).take(TasteAffinity.MIN_SCORED - 1)
        assertNull(TasteAffinity.affinity(list(100, 9, 5), few))
    }

    @Test
    fun theSameScoreForEverythingGivesNoAffinity() {
        assertNull(TasteAffinity.affinity(list(100, 9, 5), list(200, 7, 7)))
    }

    @Test
    fun unscoredAnimeDontCount() {
        val unscored = (0 until 20).map { romance(400 + it, 0) }
        assertEquals(
            TasteAffinity.affinity(list(100, 9, 5), list(200, 6, 8)),
            TasteAffinity.affinity(list(100, 9, 5) + unscored, list(200, 6, 8)),
        )
    }

    @Test
    fun genresThemesAndDemographicsAreSeparateGroups() {
        val attributes = TasteAffinity.attributes(action(1, 9).node)
        assertTrue(Attribute(Group.GENRE, "1") in attributes)
        assertTrue(Attribute(Group.THEME, "38") in attributes)
        assertTrue(Attribute(Group.DEMOGRAPHIC, "27") in attributes)
        assertTrue(Attribute(Group.STUDIO, "858") in attributes)
        assertTrue(Attribute(Group.DECADE, "2010") in attributes)
    }
}
