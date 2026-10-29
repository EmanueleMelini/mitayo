package it.emanuelemelini.mitayo.data.anilist

import it.emanuelemelini.mitayo.data.db.ListStatus
import kotlin.math.max

/** An anime of the MAL list, in MAL's terms. */
data class MalSide(val status: ListStatus, val episodes: Int, val score: Int)

/** An anime of the AniList list: [scoreRaw] on 0-100, whatever the user's format. */
data class AniListSide(val status: AniListStatus, val progress: Int, val scoreRaw: Int)

/** What to send to MAL; [score] null keeps the one on MAL. */
data class MalWrite(val status: ListStatus, val episodes: Int, val score: Int?)

/** What to send to AniList; [scoreRaw] null keeps the one on AniList. */
data class AniListWrite(val status: AniListStatus, val progress: Int, val scoreRaw: Int?)

/** Both sides at the end of the last sync of an anime; a side is null when the anime wasn't in that list. */
data class SyncBase(val mal: MalSide?, val aniList: AniListSide?)

sealed interface Decision {
    /** The two lists already say the same. */
    data object Agree : Decision

    /** Brings a change to the other list (or both lists to their merge); a write null leaves that list as it is. */
    data class Write(val mal: MalWrite?, val aniList: AniListWrite?) : Decision

    /** Different statuses or scores, or an anime removed from one list: the user chooses. */
    data object Conflict : Decision
}

/**
 * The sync of one anime between MAL and AniList. With the [SyncBase] of the last sync, a change made
 * on one side only goes to the other; the first time, and when both sides changed, the lists are
 * merged "in positive": what is on one side only is added to the other, the episodes are the most
 * watched, a missing score is filled. Different statuses or scores, and removals, are conflicts.
 */
object Reconciler {

    fun decide(mal: MalSide?, aniList: AniListSide?, base: SyncBase?, format: ScoreFormat): Decision {
        if (same(mal, aniList, format)) return Decision.Agree
        if (base == null) return merge(mal, aniList, format, firstSync = true)
        val malChanged = mal != base.mal
        val aniListChanged = !sameAniList(aniList, base.aniList)
        return when {
            malChanged && !aniListChanged -> mal?.let { Decision.Write(null, toAniList(it, aniList, format)) } ?: Decision.Conflict
            aniListChanged && !malChanged -> aniList?.let { Decision.Write(toMal(it, mal, format), null) } ?: Decision.Conflict
            malChanged -> merge(mal, aniList, format, firstSync = false)
            // Neither changed and still different: a copy didn't go through
            else -> Decision.Conflict
        }
    }

    /**
     * The same status, episodes and score. The episodes of a completed anime don't count: each site
     * completes it with its own count (AniList has 13 episodes where MAL has 12, or the other way
     * round), and a rewatch has its own.
     */
    fun same(mal: MalSide?, aniList: AniListSide?, format: ScoreFormat): Boolean {
        if (mal == null || aniList == null) return mal == null && aniList == null
        return aniList.status.matches(mal.status) &&
            (mal.status == ListStatus.COMPLETED || aniList.progress == mal.episodes) &&
            Scores.match(mal.score, aniList.scoreRaw, format)
    }

    /** [mal] copied to AniList; the score only when it isn't already the same. */
    fun toAniList(mal: MalSide, current: AniListSide?, format: ScoreFormat): AniListWrite {
        val sameScore = current != null && Scores.match(mal.score, current.scoreRaw, format)
        return AniListWrite(
            status = AniListStatus.fromMal(mal.status),
            progress = mal.episodes,
            scoreRaw = Scores.toAniList(mal.score).takeUnless { sameScore || (current == null && mal.score == 0) },
        )
    }

    /** [aniList] copied to MAL; a rewatch keeps the episodes on MAL. */
    fun toMal(aniList: AniListSide, current: MalSide?, format: ScoreFormat): MalWrite {
        val sameScore = current != null && Scores.match(current.score, aniList.scoreRaw, format)
        val episodes = if (aniList.status == AniListStatus.REPEATING && current != null) current.episodes else aniList.progress
        return MalWrite(
            status = aniList.status.toMal(),
            episodes = episodes,
            score = Scores.toMal(aniList.scoreRaw).takeUnless { sameScore || (current == null && aniList.scoreRaw == 0) },
        )
    }

    private fun merge(mal: MalSide?, aniList: AniListSide?, format: ScoreFormat, firstSync: Boolean): Decision {
        if (mal == null || aniList == null) {
            // Only on one side: the first time it's added to the other, later it was removed
            if (!firstSync) return Decision.Conflict
            return when {
                mal != null -> Decision.Write(null, toAniList(mal, null, format))
                aniList != null -> Decision.Write(toMal(aniList, null, format), null)
                else -> Decision.Agree
            }
        }
        if (!aniList.status.matches(mal.status)) return Decision.Conflict
        if (mal.score > 0 && aniList.scoreRaw > 0 && !Scores.match(mal.score, aniList.scoreRaw, format)) {
            return Decision.Conflict
        }
        // Completed (or rewatched): each list keeps its own episodes
        val completed = mal.status == ListStatus.COMPLETED
        val episodes = if (completed) mal.episodes else max(mal.episodes, aniList.progress)
        val malScore = Scores.toMal(aniList.scoreRaw).takeIf { mal.score == 0 && aniList.scoreRaw > 0 }
        val aniListScore = Scores.toAniList(mal.score).takeIf { aniList.scoreRaw == 0 && mal.score > 0 }
        val malWrite = MalWrite(mal.status, episodes, malScore).takeIf { episodes > mal.episodes || malScore != null }
        val aniListProgress = if (completed) aniList.progress else episodes
        val aniListWrite = AniListWrite(aniList.status, aniListProgress, aniListScore)
            .takeIf { aniListProgress > aniList.progress || aniListScore != null }
        return if (malWrite == null && aniListWrite == null) Decision.Agree else Decision.Write(malWrite, aniListWrite)
    }

    private fun sameAniList(a: AniListSide?, b: AniListSide?): Boolean =
        if (a == null || b == null) a == null && b == null else a.status == b.status && a.progress == b.progress && a.scoreRaw == b.scoreRaw
}
