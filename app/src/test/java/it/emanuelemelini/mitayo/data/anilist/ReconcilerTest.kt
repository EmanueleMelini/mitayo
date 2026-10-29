package it.emanuelemelini.mitayo.data.anilist

import it.emanuelemelini.mitayo.data.anilist.Decision.Agree
import it.emanuelemelini.mitayo.data.anilist.Decision.Conflict
import it.emanuelemelini.mitayo.data.anilist.Decision.Write
import it.emanuelemelini.mitayo.data.db.ListStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcilerTest {

    private val tenPoints = ScoreFormat.POINT_10

    private fun mal(status: ListStatus, episodes: Int = 0, score: Int = 0) = MalSide(status, episodes, score)

    private fun ani(status: AniListStatus, progress: Int = 0, raw: Int = 0) = AniListSide(status, progress, raw)

    @Test
    fun theSameEntryAgrees() {
        val decision = Reconciler.decide(mal(ListStatus.COMPLETED, 12, 8), ani(AniListStatus.COMPLETED, 12, 80), null, tenPoints)
        assertEquals(Agree, decision)
    }

    @Test
    fun firstSyncCopiesWhatIsOnOneSideOnly() {
        assertEquals(
            Write(null, AniListWrite(AniListStatus.CURRENT, 5, 70)),
            Reconciler.decide(mal(ListStatus.WATCHING, 5, 7), null, null, tenPoints),
        )
        assertEquals(
            Write(MalWrite(ListStatus.ON_HOLD, 3, null), null),
            Reconciler.decide(null, ani(AniListStatus.PAUSED, 3), null, tenPoints),
        )
    }

    @Test
    fun firstSyncTakesTheMostEpisodesAndFillsTheMissingScore() {
        val decision = Reconciler.decide(mal(ListStatus.WATCHING, 4, 0), ani(AniListStatus.CURRENT, 6, 90), null, tenPoints)
        assertEquals(Write(MalWrite(ListStatus.WATCHING, 6, 9), null), decision)
        val other = Reconciler.decide(mal(ListStatus.WATCHING, 8, 7), ani(AniListStatus.CURRENT, 6, 0), null, tenPoints)
        assertEquals(Write(null, AniListWrite(AniListStatus.CURRENT, 8, 70)), other)
    }

    @Test
    fun differentStatusesOrScoresAreConflicts() {
        assertEquals(Conflict, Reconciler.decide(mal(ListStatus.DROPPED, 3), ani(AniListStatus.CURRENT, 3), null, tenPoints))
        assertEquals(Conflict, Reconciler.decide(mal(ListStatus.COMPLETED, 12, 6), ani(AniListStatus.COMPLETED, 12, 90), null, tenPoints))
    }

    @Test
    fun aChangeOnOneSideOnlyGoesToTheOther() {
        val base = SyncBase(mal(ListStatus.WATCHING, 5, 0), ani(AniListStatus.CURRENT, 5))
        // Marked dropped on MAL's site
        assertEquals(
            Write(null, AniListWrite(AniListStatus.DROPPED, 5, null)),
            Reconciler.decide(mal(ListStatus.DROPPED, 5, 0), ani(AniListStatus.CURRENT, 5), base, tenPoints),
        )
        // Completed with a score on AniList's site
        assertEquals(
            Write(MalWrite(ListStatus.COMPLETED, 12, 8), null),
            Reconciler.decide(mal(ListStatus.WATCHING, 5, 0), ani(AniListStatus.COMPLETED, 12, 80), base, tenPoints),
        )
    }

    @Test
    fun aRemovalIsAlwaysAConflict() {
        val base = SyncBase(mal(ListStatus.COMPLETED, 12, 8), ani(AniListStatus.COMPLETED, 12, 80))
        assertEquals(Conflict, Reconciler.decide(null, ani(AniListStatus.COMPLETED, 12, 80), base, tenPoints))
        assertEquals(Conflict, Reconciler.decide(mal(ListStatus.COMPLETED, 12, 8), null, base, tenPoints))
    }

    @Test
    fun changesOnBothSidesMergeOrConflict() {
        val base = SyncBase(mal(ListStatus.WATCHING, 5), ani(AniListStatus.CURRENT, 5))
        assertEquals(
            Write(null, AniListWrite(AniListStatus.CURRENT, 7, null)),
            Reconciler.decide(mal(ListStatus.WATCHING, 7), ani(AniListStatus.CURRENT, 6), base, tenPoints),
        )
        assertEquals(Conflict, Reconciler.decide(mal(ListStatus.COMPLETED, 12), ani(AniListStatus.DROPPED, 6), base, tenPoints))
    }

    @Test
    fun aCopyThatNeverArrivedIsAConflict() {
        val base = SyncBase(mal(ListStatus.COMPLETED, 12), ani(AniListStatus.CURRENT, 3))
        assertEquals(Conflict, Reconciler.decide(mal(ListStatus.COMPLETED, 12), ani(AniListStatus.CURRENT, 3), base, tenPoints))
    }

    @Test
    fun aRewatchMatchesCompletedWhateverTheEpisodes() {
        val decision = Reconciler.decide(mal(ListStatus.COMPLETED, 12, 8), ani(AniListStatus.REPEATING, 3, 80), null, tenPoints)
        assertEquals(Agree, decision)
    }

    /** AniList completes with its own count: 13 episodes where MAL has 12 (Kimi ni Todoke 2nd season). */
    @Test
    fun aCompletedAnimeMatchesWhateverTheEpisodes() {
        assertEquals(Agree, Reconciler.decide(mal(ListStatus.COMPLETED, 12, 8), ani(AniListStatus.COMPLETED, 13, 80), null, tenPoints))
        val base = SyncBase(mal(ListStatus.COMPLETED, 12), ani(AniListStatus.COMPLETED, 13))
        assertEquals(Agree, Reconciler.decide(mal(ListStatus.COMPLETED, 12), ani(AniListStatus.COMPLETED, 13), base, tenPoints))
        // Only the missing score moves, each list keeps its episodes
        assertEquals(
            Write(MalWrite(ListStatus.COMPLETED, 12, 9), null),
            Reconciler.decide(mal(ListStatus.COMPLETED, 12, 0), ani(AniListStatus.COMPLETED, 16, 90), null, tenPoints),
        )
    }

    @Test
    fun decimalScoresAreNotOverwrittenByTheRoundedMalOne() {
        val format = ScoreFormat.POINT_10_DECIMAL
        val base = SyncBase(mal(ListStatus.WATCHING, 5, 9), ani(AniListStatus.CURRENT, 5, 85))
        // Completed on MAL, score unchanged: AniList keeps its 8.5
        assertEquals(
            Write(null, AniListWrite(AniListStatus.COMPLETED, 12, null)),
            Reconciler.decide(mal(ListStatus.COMPLETED, 12, 9), ani(AniListStatus.CURRENT, 5, 85), base, format),
        )
    }
}
