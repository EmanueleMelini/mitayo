package it.emanuelemelini.mitayo.data.reminders

import it.emanuelemelini.mitayo.data.AnimeSeason
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

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

    /** MAL broadcast times are in Japan. */
    private val JAPAN: ZoneId = ZoneId.of("Asia/Tokyo")

    /**
     * The last weekly broadcast at or before [time], from MAL's [day] (monday... sunday) and
     * "HH:mm" in Japan; late night slots like "25:30" belong to the night after [day]. null when
     * MAL's values can't be read.
     */
    fun lastBroadcast(day: String, startTime: String, time: ZonedDateTime): ZonedDateTime? {
        val dayOfWeek = DayOfWeek.entries.firstOrNull { it.name.equals(day, ignoreCase = true) } ?: return null
        val parts = startTime.split(':').map { it.toIntOrNull() ?: return null }
        if (parts.size != 2 || parts[0] !in 0..47 || parts[1] !in 0..59) return null
        val inJapan = time.withZoneSameInstant(JAPAN)
        val candidate = inJapan.toLocalDate().with(TemporalAdjusters.previousOrSame(dayOfWeek))
            .atStartOfDay(JAPAN).plusHours(parts[0].toLong()).plusMinutes(parts[1].toLong())
        return if (candidate.isAfter(inJapan)) candidate.minusWeeks(1) else candidate
    }

    /** A new episode aired after [from] and up to [to]. */
    fun airedBetween(day: String, startTime: String, from: ZonedDateTime, to: ZonedDateTime): Boolean =
        lastBroadcast(day, startTime, to)?.isAfter(from) == true
}
