package it.emanuelemelini.mitayo.data.reminders

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import it.emanuelemelini.mitayo.MitayoApp
import kotlinx.coroutines.CancellationException

/** Shows one reminder: when it needs the network, as soon as there is a connection. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val type = ReminderType.entries.find { it.name == inputData.getString(KEY_TYPE) } ?: return Result.failure()
        val container = (applicationContext as MitayoApp).container
        return try {
            container.reminderNotifier.show(type)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // GitHub or MAL not answering: a few more tries, then tomorrow's alarm
            Log.i(TAG, "$type failed: ${e.message}")
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val MAX_RETRIES = 3
        private const val TAG = "ReminderWorker"

        fun enqueue(context: Context, type: ReminderType) {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf(KEY_TYPE to type.name))
                .apply {
                    if (type.needsNetwork) {
                        setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    }
                }
                .build()
            // An occurrence still waiting for the network is replaced by the new one
            WorkManager.getInstance(context).enqueueUniqueWork("reminder_${type.name}", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
