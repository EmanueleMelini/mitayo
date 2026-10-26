package it.emanuelemelini.mitayo.data.reminders

import androidx.annotation.StringRes
import it.emanuelemelini.mitayo.R
import java.time.LocalTime

/**
 * Local notifications, scheduled on the phone (no push server). The constant names are stored
 * in the preferences: don't rename them. New types go at the end: the ordinal is part of the
 * notification and alarm ids.
 */
enum class ReminderType(
    @StringRes val labelRes: Int,
    @StringRes val hintRes: Int,
    val defaultTime: LocalTime,
    /** Weekly reminders fire on [ReminderSchedule.WEEKLY_DAY] only. */
    val weekly: Boolean = false,
    /** It asks GitHub or MAL first: the work waits for a connection. */
    val needsNetwork: Boolean = false,
) {
    /** A new Mitayo release on GitHub, once per version. */
    UPDATE(R.string.reminder_update, R.string.reminder_update_hint, LocalTime.of(10, 0), needsNetwork = true),

    /** The first days of a new anime season, once per season. */
    SEASON(R.string.reminder_season, R.string.reminder_season_hint, LocalTime.of(10, 0), needsNetwork = true),

    /** An invitation to a few swipes, with the anime still to see. */
    WEEKLY(R.string.reminder_weekly, R.string.reminder_weekly_hint, LocalTime.of(18, 0), weekly = true),
}

/** Every reminder starts off: the user turns on the ones they want. */
data class ReminderConfig(
    val type: ReminderType,
    val enabled: Boolean = false,
    val time: LocalTime = type.defaultTime,
)
