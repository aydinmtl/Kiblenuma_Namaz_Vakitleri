package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SelectionConcurrencyTest {
    @Test
    fun delayedLocationCallbackAndNewAutomaticWorkCannotDefeatPendingManualChoice() {
        val guard = SelectionGuard()
        var city = "A"
        val delayedLocation = guard.automatic { city }!!
        val manual = guard.beginManual()
        assertNull(guard.automatic { city })
        assertFalse(guard.apply(delayedLocation.first) { city = "old auto" })
        assertTrue(guard.finishManual(manual) { city = "B" })
        assertEquals("B", city)
        assertNotNull(guard.automatic { city })
    }

    @Test
    fun ordinaryRefreshCannotInvalidateManualReservation() {
        val guard = SelectionGuard()
        val manual = guard.beginManual()
        val refresh = guard.capture { "A" }
        assertFalse(guard.apply(refresh.first) { fail("refresh won over manual choice") })
        assertTrue(guard.finishManual(manual) {})
    }

    @Test
    fun cancelledOrFailedManualWorkReleasesReservationAndCannotSaveLater() {
        val guard = SelectionGuard()
        val cancelled = guard.beginManual()
        guard.cancelManual(cancelled)
        assertNotNull(guard.automatic { "A" })
        assertFalse(guard.finishManual(cancelled) { fail("cancelled manual result applied") })
        val failed = guard.beginManual()
        try {
            guard.finishManual(failed) { throw IllegalStateException("download failed") }
            fail("exception expected")
        } catch (_: IllegalStateException) { }
        assertNotNull(guard.automatic { "A" })
    }

    @Test
    fun cancellingOlderManualRequestDoesNotReleaseNewerReservation() {
        val guard = SelectionGuard()
        val old = guard.beginManual()
        val newer = guard.beginManual()
        guard.cancelManual(old)
        assertNull(guard.automatic { "A" })
        assertFalse(guard.finishManual(old) {})
        assertTrue(guard.finishManual(newer) {})
    }

    @Test
    fun successfulDataRefreshInvalidatesEarlierAlarmSnapshotEvenForSameCity() {
        val guard = SelectionGuard()
        val oldAlarm = guard.capture { "A" }
        val refresh = guard.capture { "A" }
        assertTrue(guard.apply(refresh.first) {})
        assertFalse(guard.ifCurrent(oldAlarm.first) { fail("old data published") })
    }

    @Test
    fun releasingManualReservationDoesNotReviveEarlierLocationCallback() {
        val guard = SelectionGuard()
        val oldLocation = guard.automatic { "A" }!!
        val manual = guard.beginManual()
        guard.cancelManual(manual)
        val freshLocation = guard.automatic { "A" }!!
        assertFalse(guard.isCurrentRequest(oldLocation.first))
        assertTrue(guard.isCurrentRequest(freshLocation.first))
        assertFalse(guard.apply(oldLocation.first) { fail("cancel revived stale callback") })
        assertTrue(guard.apply(freshLocation.first) {})
    }

    @Test
    fun changingSettingsInvalidatesPreviouslyCapturedAlarmData() {
        val guard = SelectionGuard()
        val oldAlarm = guard.capture { "old method" }
        guard.change { }
        assertFalse(guard.ifCurrent(oldAlarm.first) { fail("old settings published") })
    }
}
