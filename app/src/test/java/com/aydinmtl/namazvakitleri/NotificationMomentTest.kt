package com.aydinmtl.namazvakitleri

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationMomentTest {
    @Test
    fun alarm_and_service_payload_preserve_selected_place_clock_and_day() {
        val restored = Moment.fromEpochMillis(2, Instant.parse("2026-11-08T09:53:00Z").toEpochMilli(),
            LocalTime.of(12, 53).toSecondOfDay(), LocalDate.of(2026, 11, 8).toEpochDay(), ZoneId.of("Europe/Zurich"))
        assertEquals(LocalTime.of(10, 53), restored.at.toLocalTime())
        assertEquals("12:53", restored.clock.format(HHMM))
        assertEquals(LocalDate.of(2026, 11, 8), restored.day)
        val serviceCopy = Moment.fromEpochMillis(restored.index, restored.instant.toEpochMilli(),
            restored.clock.toSecondOfDay(), restored.day.toEpochDay(), ZoneId.of("Europe/Zurich"))
        assertEquals(restored, serviceCopy)
    }

    @Test
    fun alarm_payload_keeps_place_day_when_phone_is_on_previous_day() {
        val restored = Moment.fromEpochMillis(0, Instant.parse("2026-03-28T19:30:00Z").toEpochMilli(),
            LocalTime.of(4, 30).toSecondOfDay(), LocalDate.of(2026, 3, 29).toEpochDay(), ZoneId.of("Europe/Zurich"))
        assertEquals(LocalDate.of(2026, 3, 28), restored.at.toLocalDate())
        assertEquals(LocalDate.of(2026, 3, 29), restored.day)
        assertEquals("04:30", restored.clock.format(HHMM))
    }

    @Test
    fun adhan_stop_does_not_remove_daily_question() {
        // Model NotificationManager's untagged-ID replacement/removal behavior.
        val posted = mutableMapOf(NotificationIds.ADHAN to "adhan", NotificationIds.DAILY to "question")
        posted.remove(NotificationIds.ADHAN)
        assertEquals("question", posted[NotificationIds.DAILY])
        val prayerAndReminders = PRAYER_NAMES.indices.flatMap { listOf(NotificationIds.prayer(it), NotificationIds.reminder(it)) }
        assertFalse(NotificationIds.ADHAN in prayerAndReminders)
        assertFalse(NotificationIds.DAILY in prayerAndReminders)
        assertEquals(12, prayerAndReminders.toSet().size)
    }
}
