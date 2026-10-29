package it.emanuelemelini.mitayo.data.anilist

import it.emanuelemelini.mitayo.data.anilist.AniListSession.Companion.NOTICE_END
import it.emanuelemelini.mitayo.data.anilist.AniListSession.Companion.NOTICE_START
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncNoticesTest {

    private val opened = 1_000_000L

    private fun summary(
        state: SyncSessionState,
        startedAt: Long,
        endedAt: Long? = null,
        notice: Int = 0,
        toAniList: Int = 0,
        toMal: Int = 0,
        notFound: Int = 0,
        found: Int = 0,
        remaining: Int = 0,
    ) = AniListSessionSummary(
        session = AniListSession(
            id = 7,
            kind = SyncSessionKind.SYNC,
            state = state,
            startedAt = startedAt,
            updatedAt = endedAt ?: startedAt,
            endedAt = endedAt,
            notice = notice,
        ),
        toAniList = toAniList,
        toMal = toMal,
        notFound = notFound,
        found = found,
        foundTitle = "Yosuga no Sora".takeIf { found > 0 },
        conflicts = 0,
        failed = 0,
        remaining = remaining,
        errorCode = null,
    )

    @Test
    fun aSyncStartedInTheAppSaysWhatItFound() {
        val (notices, notice) = syncNotices(summary(SyncSessionState.RUNNING, opened + 500, remaining = 12, notFound = 2, found = 1), opened)
        assertEquals(
            listOf(
                SyncNotice.Running(7, away = false, remaining = 12),
                SyncNotice.NotFound(7, 2),
                SyncNotice.Found(7, 1, "Yosuga no Sora"),
            ),
            notices,
        )
        assertEquals(NOTICE_START, notice)
    }

    @Test
    fun theEndComesOnceAfterTheStart() {
        val done = summary(SyncSessionState.DONE, opened + 500, endedAt = opened + 9_000, notice = NOTICE_START, toAniList = 12, notFound = 2)
        assertEquals(listOf(SyncNotice.Finished(7, away = false, toAniList = 12, toMal = 0)) to NOTICE_END, syncNotices(done, opened))
        assertEquals(emptyList<SyncNotice>() to NOTICE_END, syncNotices(done.copy(session = done.session.copy(notice = NOTICE_END)), opened))
    }

    @Test
    fun aSyncWithNothingToSendSaysItsFindingsAndTheEnd() {
        val done = summary(SyncSessionState.DONE, opened + 500, endedAt = opened + 900, toMal = 1, found = 1)
        assertEquals(
            listOf(SyncNotice.Found(7, 1, "Yosuga no Sora"), SyncNotice.Finished(7, away = false, toAniList = 0, toMal = 1)) to NOTICE_END,
            syncNotices(done, opened),
        )
    }

    @Test
    fun whatHappenedWhileAwayOpensItsSession() {
        val ended = summary(SyncSessionState.DONE, opened - 3_600_000, endedAt = opened - 600_000, notice = NOTICE_START, toAniList = 40, notFound = 1)
        assertEquals(listOf(SyncNotice.Finished(7, away = true, toAniList = 40, toMal = 0)) to NOTICE_END, syncNotices(ended, opened))
        val running = summary(SyncSessionState.RUNNING, opened - 600_000, remaining = 30, notFound = 1)
        assertEquals(listOf(SyncNotice.Running(7, away = true, remaining = 30)) to NOTICE_START, syncNotices(running, opened))
    }

    @Test
    fun theAppsOwnSyncAtTheOpeningIsNotAway() {
        // Started by the app as it opened, a moment before the notices were watched
        val running = summary(SyncSessionState.RUNNING, opened - 2_000, remaining = 3)
        assertEquals(listOf(SyncNotice.Running(7, away = false, remaining = 3)), syncNotices(running, opened).first)
    }
}
