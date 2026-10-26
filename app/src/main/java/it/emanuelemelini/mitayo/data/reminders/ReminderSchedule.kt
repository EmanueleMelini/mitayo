package it.emanuelemelini.mitayo.data.reminders

import it.emanuelemelini.mitayo.data.AnimeSeason
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderSchedule {

    /** Day of the weekly reminder. */
    val WEEKLY_DAY: DayOfWeek = DayOfWeek.SUNDAY

    /** The season notification waits for the new season's lists for at most these days. */
    const val SEASON_NOTICE_DAYS = 14L

    /**
     * Next moment strictly after [now] at [time]: today or tomorrow for daily reminders, the
     * next [WEEKLY_DAY] for weekly ones. Daylight saving gaps are handled by [ZonedDateTime].
     */
    fun nextTrigger(type: ReminderType, time: LocalTime, now: ZonedDateTime): ZonedDateTime {
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            if (type.weekly && date.dayOfWeek != WEEKLY_DAY) continue
            val candidate = date.atTime(time).atZone(now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("No trigger found for $type")
    }

    /**
     * The season to announce on [today]: the current one, in its first [SEASON_NOTICE_DAYS] and
     * if [lastNotified] (a [seasonKey]) isn't it already. Turned on in the middle of a season,
     * the reminder waits for the next one.
     */
    fun seasonToAnnounce(today: LocalDate, lastNotified: String?): AnimeSeason? {
        val season = AnimeSeason.current(today)
        val start = LocalDate.of(today.year, (today.monthValue - 1) / 3 * 3 + 1, 1)
        if (today.isAfter(start.plusDays(SEASON_NOTICE_DAYS - 1))) return null
        return season.takeIf { seasonKey(it) != lastNotified }
    }

    fun seasonKey(season: AnimeSeason): String = "${season.year}-${season.season}"
}
