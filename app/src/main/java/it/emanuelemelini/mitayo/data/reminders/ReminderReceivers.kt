package it.emanuelemelini.mitayo.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.emanuelemelini.mitayo.MitayoApp
import kotlinx.coroutines.launch

/**
 * Fired by the alarm: the reminder itself goes to [ReminderWorker] (it may need GitHub or MAL,
 * longer than a receiver may take), then the next occurrence is scheduled.
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val type = ReminderType.entries.find { it.name == intent.getStringExtra(EXTRA_TYPE) } ?: return
        val container = (context.applicationContext as MitayoApp).container
        ReminderWorker.enqueue(context, type)
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.reminderScheduler.reschedule(type)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_TYPE = "reminder_type"
    }
}

/** Alarms are lost on reboot and must follow time or time zone changes: schedules them again. */
class ReminderRescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = (context.applicationContext as MitayoApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.reminderScheduler.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
