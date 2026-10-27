package it.emanuelemelini.mitayo

import it.emanuelemelini.mitayo.data.AnimeSeason
import it.emanuelemelini.mitayo.data.Category
import it.emanuelemelini.mitayo.data.GenreGroup
import it.emanuelemelini.mitayo.data.GenreGroups
import it.emanuelemelini.mitayo.data.db.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CategoryTest {

    @Test
    fun keysRoundTrip() {
        (Category.specials + Category.OfGenre(62) + Category.OfDecade(2010) + Category.OfDecade(Category.OfDecade.BEFORE) +
            Category.OfUser("LeleMims") + ListStatus.entries.map { Category.OfStatus(it) } +
            Category.OfSeason(2019, "spring") + Category.OfSeason(Category.OfSeason.FIRST_YEAR, "fall")).forEach { category ->
            assertEquals(category, Category.fromKey(category.key))
        }
    }

    @Test
    fun unknownKeysAreRejected() {
        assertNull(Category.fromKey("genre:abc"))
        assertNull(Category.fromKey("nope"))
        assertNull(Category.fromKey("decade:2015"))
        assertNull(Category.fromKey("decade:1970"))
        assertNull(Category.fromKey("season:2019:monsoon"))
        assertNull(Category.fromKey("season:1950:spring"))
        assertNull(Category.fromKey("season:2019"))
    }

    @Test
    fun upcomingAndOtherSeasonsAreRemote() {
        assertEquals(Category.Upcoming, Category.fromKey("upcoming"))
        assertEquals(true, Category.Upcoming.isRemote)
        assertEquals(true, Category.OfSeason(2019, "spring").isRemote)
        // The current season keeps its own key, the others don't clash with it
        assertEquals(Category.Season, Category.fromKey("season"))
    }

    @Test
    fun seasonsFollowMalMonths() {
        assertEquals(AnimeSeason(2026, "winter"), AnimeSeason.current(LocalDate.of(2026, 3, 31)))
        assertEquals(AnimeSeason(2026, "spring"), AnimeSeason.current(LocalDate.of(2026, 4, 1)))
        assertEquals(AnimeSeason(2026, "fall"), AnimeSeason.current(LocalDate.of(2026, 10, 23)))
    }

    @Test
    fun genreIdsAreGrouped() {
        assertEquals(GenreGroup.GENRE, GenreGroups.of(1)) // Action
        assertEquals(GenreGroup.THEME, GenreGroups.of(62)) // Isekai
        assertEquals(GenreGroup.DEMOGRAPHIC, GenreGroups.of(27)) // Shounen
    }

    @Test
    fun decadesCoverTenYearsAndTheOlderOnes() {
        assertEquals(2010..2019, Category.OfDecade(2010).years)
        assertEquals(0..1979, Category.OfDecade(Category.OfDecade.BEFORE).years)
    }
}
