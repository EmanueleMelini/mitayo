package it.emanuelemelini.mitayo.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import it.emanuelemelini.mitayo.MitayoApp
import it.emanuelemelini.mitayo.data.db.ListStatus
import it.emanuelemelini.mitayo.data.db.OutboxDao
import it.emanuelemelini.mitayo.data.db.OutboxOp
import it.emanuelemelini.mitayo.data.mal.MalApi
import it.emanuelemelini.mitayo.data.mal.MalHttpException
import it.emanuelemelini.mitayo.data.mal.NotLoggedInException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class FlushResult { DONE, RETRY, NOT_LOGGED_IN }

/**
 * Changes to the MAL list, saved before they're sent: a swipe is instant and works offline,
 * and WorkManager delivers it as soon as there's a connection.
 */
class Outbox(context: Context, private val dao: OutboxDao) {
    private val workManager = WorkManager.getInstance(context)

    val count: Flow<Int> = dao.observeCount()

    suspend fun pending(): List<OutboxOp> = dao.all()

    /** The last change wins: one not sent yet for the same anime is dropped. [score] null: unchanged. */
    suspend fun enqueue(animeId: Int, status: ListStatus?, episodesWatched: Int, score: Int? = null) {
        dao.deleteFor(animeId)
        dao.insert(
            OutboxOp(
                animeId = animeId,
                status = status,
                episodesWatched = episodesWatched,
                createdAt = System.currentTimeMillis(),
                score = score,
            )
        )
        schedule()
    }

    fun schedule() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // Appended to a running flush, so a change made while it runs is never missed
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    suspend fun clear() {
        workManager.cancelUniqueWork(WORK_NAME)
        dao.clear()
    }

    /** Sends everything, oldest first, one request at a time. */
    suspend fun flush(api: MalApi): FlushResult {
        while (true) {
            val op = dao.first() ?: return FlushResult.DONE
            try {
                val status = op.status
                if (status == null) {
                    api.deleteListStatus(op.animeId)
                } else {
                    api.updateListStatus(op.animeId, status.apiValue, op.episodesWatched, op.score)
                }
                dao.delete(op.id)
            } catch (_: NotLoggedInException) {
                return FlushResult.NOT_LOGGED_IN
            } catch (e: MalHttpException) {
                // 403 is also MAL's "too many requests"; other client errors won't improve by
                // retrying, and the next list sync shows the real state again
                if (e.code == 403 || e.code == 429 || e.code >= 500) return FlushResult.RETRY
                dao.delete(op.id)
            } catch (_: IOException) {
                return FlushResult.RETRY
            }
            delay(OP_INTERVAL_MS)
        }
    }

    private companion object {
        const val WORK_NAME = "mal_outbox"
        const val OP_INTERVAL_MS = 350L
    }
}

class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as MitayoApp).container
        return when (container.outbox.flush(container.api)) {
            FlushResult.DONE -> Result.success()
            FlushResult.RETRY -> Result.retry()
            // Kept: the next login schedules them again
            FlushResult.NOT_LOGGED_IN -> Result.failure()
        }
    }
}
