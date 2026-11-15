package com.aydinmtl.namazvakitleri

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PrayerSnapshotTest {
    private val today = LocalDate.of(2026, 3, 29)
    private fun snapshot(id: String, longitude: Double, imsak: String, noon: String) = PrayerSnapshot(
        districtId = id,
        timesJson = """[{"MiladiTarihKisa":"29.03.2026","Imsak":"$imsak","Gunes":"06:00","Ogle":"$noon","Ikindi":"15:00","Aksam":"18:00","Yatsi":"20:00"}]""",
        point = 35.0 to longitude,
        method = CalcMethod.DIYANET,
        hanafi = false,
    )

    @Test
    fun writerBetweenCaptureAndDerivationCannotMixTimetableAndCoordinates() {
        val guard = SelectionGuard()
        val reader = PrayerTimeReader()
        var selected = snapshot("A", 139.7, "04:30", "11:51")
        val captured = CountDownLatch(1)
        val resume = CountDownLatch(1)
        val result = AtomicReference<List<PrayerDay>>()
        val failure = AtomicReference<Throwable>()
        val worker = Thread {
            try {
                val state = guard.capture { selected }.second
                captured.countDown()
                check(resume.await(5, TimeUnit.SECONDS))
                result.set(reader.days(state, today, ZoneOffset.UTC))
            } catch (t: Throwable) { failure.set(t) }
        }
        worker.start()
        try {
            assertTrue(captured.await(5, TimeUnit.SECONDS))
            guard.change { selected = snapshot("B", 0.0, "05:00", "12:08") }
            val newer = reader.days(guard.capture { selected }.second, today, ZoneOffset.UTC)
            assertEquals(Instant.parse("2026-03-29T05:00:00Z"), newer.single().instants!!.first())
        } finally {
            resume.countDown()
            worker.join(6000)
        }
        assertFalse(worker.isAlive)
        failure.get()?.let { throw AssertionError(it) }
        val old = result.get().single()
        assertEquals(LocalTime.of(4, 30), old.times.first())
        assertEquals(Instant.parse("2026-03-28T19:30:00Z"), old.instants!!.first())
        val newerAgain = reader.days(guard.capture { selected }.second, today, ZoneOffset.UTC)
        assertEquals(LocalTime.of(5, 0), newerAgain.single().times.first())
        assertEquals(Instant.parse("2026-03-29T05:00:00Z"), newerAgain.single().instants!!.first())
    }

    @Test
    fun equalDateRangeWithRefreshedContentUsesNewTimesAndLeavesOldSnapshotIntact() {
        val reader = PrayerTimeReader()
        val old = snapshot("A", 139.7, "04:30", "11:51")
        val first = reader.days(old, today, ZoneOffset.UTC)
        val second = reader.days(old.copy(timesJson = old.timesJson!!.replace("04:30", "04:31")), today, ZoneOffset.UTC)
        assertEquals(Instant.parse("2026-03-28T19:30:00Z"), first.single().instants!!.first())
        assertEquals(Instant.parse("2026-03-28T19:31:00Z"), second.single().instants!!.first())
        try {
            (first.single().times as MutableList<LocalTime>)[0] = LocalTime.NOON
            fail("snapshot must not expose mutable cached clocks")
        } catch (_: UnsupportedOperationException) { }
        assertEquals(LocalTime.of(4, 30), reader.days(old, today, ZoneOffset.UTC).single().times.first())
    }

    @Test
    fun calculationSettingsAndCoordinatePairTravelWithSnapshot() {
        val old = snapshot("A", 139.7, "04:30", "11:51").copy(method = CalcMethod.MWL, hanafi = true)
        val newer = old.copy(districtId = "B", point = null, method = CalcMethod.ISNA, hanafi = false)
        assertEquals(CalcMethod.MWL, old.effectiveMethod)
        assertTrue(old.hanafi)
        assertEquals(35.0 to 139.7, old.point)
        assertEquals(CalcMethod.DIYANET, newer.effectiveMethod)
        assertFalse(newer.hanafi)
    }
    @Test
    fun calculationsUseCapturedMethodHanafiSettingAndPointAfterSelectionChanges() {
        val guard = SelectionGuard()
        val reader = PrayerTimeReader { "hicri" }
        var selected = snapshot("A", 6.14, "04:30", "12:08").copy(
            point = 46.2 to 6.14, method = CalcMethod.MWL, hanafi = true,
        )
        val old = guard.capture { selected }.second
        guard.change {
            selected = selected.copy(point = 41.0 to 29.0, method = CalcMethod.ISNA, hanafi = false)
        }
        val oldDay = reader.days(old, today, ZoneOffset.UTC).first { it.date == today }
        val expected = PrayerCalc.calculatedDay(today, 46.2, 6.14, ZoneOffset.UTC, CalcMethod.MWL, 2)!!
        assertEquals(expected.times, oldDay.times)
        val newDay = reader.days(guard.capture { selected }.second, today, ZoneOffset.UTC).first { it.date == today }
        assertNotEquals(oldDay.times, newDay.times)
        assertEquals(expected.times, reader.days(old, today, ZoneOffset.UTC).first { it.date == today }.times)
    }

    @Test
    fun rawAndDerivedDaysDoNotExposeMutableSharedLists() {
        val state = snapshot("A", 139.7, "04:30", "11:51")
        val raw = state.diyanetDays()
        val derived = PrayerTimeReader().days(state, today, ZoneOffset.UTC)
        try {
            (raw as MutableList<PrayerDay>).clear()
            fail("raw snapshot list must be immutable")
        } catch (_: UnsupportedOperationException) { }
        try {
            (derived.single().instants as MutableList<Instant>)[0] = Instant.EPOCH
            fail("derived instant list must be immutable")
        } catch (_: UnsupportedOperationException) { }
        assertEquals(Instant.parse("2026-03-28T19:30:00Z"), derived.single().instants!!.first())
    }

}
