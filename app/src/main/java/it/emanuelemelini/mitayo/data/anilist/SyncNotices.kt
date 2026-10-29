package it.emanuelemelini.mitayo.data.anilist

import it.emanuelemelini.mitayo.data.anilist.AniListSession.Companion.NOTICE_END
import it.emanuelemelini.mitayo.data.anilist.AniListSession.Companion.NOTICE_START

/**
 * An in-app notice about a sync. [away]: it happened while the app was closed or in the
 * background, so it opens its session in the log instead of the AniList page.
 */
sealed interface SyncNotice {
    val sessionId: Long
    val away: Boolean

    data class Running(override val sessionId: Long, override val away: Boolean, val remaining: Int) : SyncNotice

    data class Finished(override val sessionId: Long, override val away: Boolean, val toAniList: Int, val toMal: Int) : SyncNotice

    data class Failed(override val sessionId: Long, override val away: Boolean, val errorCode: Int) : SyncNotice

    /** Anime that went to the "Not on AniList" page. */
    data class NotFound(override val sessionId: Long, val count: Int) : SyncNotice {
        override val away = false
    }

    /** Anime of the "Not on AniList" page that AniList has now; [title] of the first. */
    data class Found(override val sessionId: Long, val count: Int, val title: String?) : SyncNotice {
        override val away = false
    }
}

/**
 * What to tell about [summary] that wasn't told yet, and the [AniListSession.notice] it reaches.
 * While the app is in the foreground (since [foregroundSince]) the start, what went to the "Not on
 * AniList" page or left it, and the end; what happened while it was away, only where the sync is.
 */
fun syncNotices(summary: AniListSessionSummary, foregroundSince: Long): Pair<List<SyncNotice>, Int> {
    val session = summary.session
    if (session.state == SyncSessionState.RUNNING) {
        if (session.notice >= NOTICE_START) return emptyList<SyncNotice>() to session.notice
        val away = session.startedAt < foregroundSince - FOREGROUND_GRACE_MS
        val running = SyncNotice.Running(session.id, away, summary.remaining)
        return (if (away) listOf(running) else listOf(running) + findings(summary)) to NOTICE_START
    }
    if (session.notice >= NOTICE_END) return emptyList<SyncNotice>() to session.notice
    val away = (session.endedAt ?: session.updatedAt) < foregroundSince - FOREGROUND_GRACE_MS
    val end = if (session.state == SyncSessionState.FAILED) {
        SyncNotice.Failed(session.id, away, summary.errorCode ?: 0)
    } else {
        SyncNotice.Finished(session.id, away, summary.toAniList, summary.toMal)
    }
    // Started and ended without a running notice (nothing to send): its findings come now
    val before = if (!away && session.notice < NOTICE_START) findings(summary) else emptyList()
    return before + end to NOTICE_END
}

private fun findings(summary: AniListSessionSummary): List<SyncNotice> = listOfNotNull(
    SyncNotice.NotFound(summary.session.id, summary.notFound).takeIf { summary.notFound > 0 },
    SyncNotice.Found(summary.session.id, summary.found, summary.foundTitle).takeIf { summary.found > 0 },
)

/** The app's own sync starts as it opens, a moment before the notices start watching. */
private const val FOREGROUND_GRACE_MS = 10_000L
