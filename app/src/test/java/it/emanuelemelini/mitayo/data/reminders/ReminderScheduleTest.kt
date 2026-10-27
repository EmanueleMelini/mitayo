package it.emanuelemelini.mitayo.data.reminders

import it.emanuelemelini.mitayo.data.AnimeSeason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderScheduleTest {

    private val rome = ZoneId.of("Europe/Rome")

    @Test
    fun broadcastIsReadInJapanTime() {
        // Saturday 00:00 in Tokyo is Friday 17:00 in Rome (October, summer time)
        val friday = ZonedDateTime.of(2026, 10, 23, 18, 0, 0, 0, rome)
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 17, 0, 0, 0, rome).toInstant(),
            ReminderSchedule.lastBroadcast("saturday", "00:00", friday)?.toInstant(),
        )
        // Before that moment, the last one is a week earlier
        assertEquals(
            ZonedDateTime.of(2026, 10, 16, 17, 0, 0, 0, rome).toInstant(),
            ReminderSchedule.lastBroadcast("saturday", "00:00", friday.withHour(16))?.toInstant(),
        )
    }

    @Test
    fun lateNightSlotsBelongToTheNightAfter() {
        // "Friday 25:30" is Saturday 01:30 in Tokyo: Friday 18:30 in Rome
        val friday = ZonedDateTime.of(2026, 10, 23, 19, 0, 0, 0, rome)
        assertEquals(
            ZonedDateTime.of(2026, 10, 23, 18, 30, 0, 0, rome).toInstant(),
            ReminderSchedule.lastBroadcast("friday", "25:30", friday)?.toInstant(),
        )
    }

    @Test
    fun episodeReminderLooksAtTheLastDay() {
        val evening = ZonedDateTime.of(2026, 10, 23, 20, 0, 0, 0, rome)
        assertEquals(true, ReminderSchedule.airedBetween("saturday", "00:00", evening.minusDays(1), evening))
        assertEquals(false, ReminderSchedule.airedBetween("wednesday", "23:00", evening.minusDays(1), evening))
    }

    @Test
    fun unreadableBroadcastsAreIgnored() {
        val now = ZonedDateTime.of(2026, 10, 23, 20, 0, 0, 0, rome)
        assertNull(ReminderSchedule.lastBroadcast("someday", "00:00", now))
        assertNull(ReminderSchedule.lastBroadcast("monday", "late", now))
        assertNull(ReminderSchedule.lastBroadcast("monday", "12:75", now))
    }

    @Test
    fun dailyReminderFiresTodayOrTomorrow() {
        val morning = ZonedDateTime.of(2026, 10, 23, 9, 0, 0, 0, rome)
        assertEquals(morning.withHour(10), ReminderSchedule.nextTrigger(ReminderType.UPDATE, LocalTime.of(10, 0), morning))
        val evening = morning.withHour(11)
        assertEquals(
            morning.plusDays(1).withHour(10),
            ReminderSchedule.nextTrigger(ReminderType.UPDATE, LocalTime.of(10, 0), evening),
        )
    }

    @Test
    fun weeklyReminderWaitsForSunday() {
        // Friday 23 October 2026
        val friday = ZonedDateTime.of(2026, 10, 23, 20, 0, 0, 0, rome)
        val next = ReminderSchedule.nextTrigger(ReminderType.WEEKLY, LocalTime.of(18, 0), friday)
        assertEquals(DayOfWeek.SUNDAY, next.dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 25), next.toLocalDate())
        // On Sunday after the time: the next Sunday
        val sundayNight = ZonedDateTime.of(2026, 10, 25, 19, 0, 0, 0, rome)
        assertEquals(
            LocalDate.of(2026, 11, 1),
            ReminderSchedule.nextTrigger(ReminderType.WEEKLY, LocalTime.of(18, 0), sundayNight).toLocalDate(),
        )
    }

    @Test
    fun daylightSavingGapMovesTheTrigger() {
        // 29 March 2026, Rome: 02:00 doesn't exist
        val night = ZonedDateTime.of(2026, 3, 29, 0, 30, 0, 0, rome)
        val next = ReminderSchedule.nextTrigger(ReminderType.UPDATE, LocalTime.of(2, 30), night)
        assertEquals(LocalDate.of(2026, 3, 29), next.toLocalDate())
        assertEquals(3, next.hour)
    }

    @Test
    fun seasonIsAnnouncedInItsFirstDaysOnly() {
        val fall = AnimeSeason(2026, "fall")
        assertEquals(fall, ReminderSchedule.seasonToAnnounce(LocalDate.of(2026, 10, 1), lastNotified = null))
        assertEquals(fall, ReminderSchedule.seasonToAnnounce(LocalDate.of(2026, 10, 14), lastNotified = "2026-summer"))
        assertNull(ReminderSchedule.seasonToAnnounce(LocalDate.of(2026, 10, 15), lastNotified = "2026-summer"))
        assertEquals(AnimeSeason(2027, "winter"), ReminderSchedule.seasonToAnnounce(LocalDate.of(2027, 1, 3), "2026-fall"))
    }

    @Test
    fun seasonIsAnnouncedOnce() {
        assertNull(ReminderSchedule.seasonToAnnounce(LocalDate.of(2026, 10, 2), lastNotified = "2026-fall"))
        assertEquals("2026-fall", ReminderSchedule.seasonKey(AnimeSeason(2026, "fall")))
    }
}
