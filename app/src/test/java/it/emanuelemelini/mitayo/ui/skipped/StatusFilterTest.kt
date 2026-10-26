package it.emanuelemelini.mitayo.ui.skipped

import it.emanuelemelini.mitayo.data.db.Anime
import it.emanuelemelini.mitayo.data.db.DeckCard
import it.emanuelemelini.mitayo.data.db.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusFilterTest {

    private fun card(id: Int, status: ListStatus?) = DeckCard(
        anime = Anime(
            id = id,
            title = "Anime $id",
            titleEn = null,
            titleJa = null,
            pictureUrl = null,
            mediaType = null,
            airingStatus = null,
            episodes = 12,
            mean = null,
            popularity = id,
            members = 0,
            seasonYear = null,
            season = null,
            nsfw = false,
            genreNames = "",
            lastSeenAt = 0,
        ),
        listStatus = status,
        listEpisodes = null,
        listScore = null,
    )

    @Test
    fun noFiltersWhenNoneIsInTheList() {
        assertTrue(filtersOf(listOf(card(1, null), card(2, null))).isEmpty())
    }

    @Test
    fun onlyTheGroupsWithAnimeAndHowMany() {
        val cards = listOf(card(1, null), card(2, ListStatus.WATCHING), card(3, ListStatus.WATCHING), card(4, ListStatus.DROPPED))
        assertEquals(
            listOf(
                StatusFilter.All to 4,
                StatusFilter.NotInList to 1,
                StatusFilter.Of(ListStatus.WATCHING) to 2,
                StatusFilter.Of(ListStatus.DROPPED) to 1,
            ),
            filtersOf(cards),
        )
    }

    @Test
    fun eachFilterMatchesItsStatus() {
        val watching = card(1, ListStatus.WATCHING)
        val notInList = card(2, null)
        assertTrue(StatusFilter.All.matches(watching) && StatusFilter.All.matches(notInList))
        assertTrue(StatusFilter.NotInList.matches(notInList) && !StatusFilter.NotInList.matches(watching))
        assertTrue(StatusFilter.Of(ListStatus.WATCHING).matches(watching))
        assertTrue(!StatusFilter.Of(ListStatus.WATCHING).matches(notInList))
    }
}
