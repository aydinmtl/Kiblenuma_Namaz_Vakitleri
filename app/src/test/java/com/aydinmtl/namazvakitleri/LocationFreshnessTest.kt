package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFreshnessTest {
    @Test
    fun rejectsOldMissingAndFutureMeasurements() {
        val now = 86_400_000_000_000L
        assertFalse(LocationFreshness.accepts(now, 1L, 180_000L))
        assertFalse(LocationFreshness.accepts(now, 0L, 180_000L))
        assertFalse(LocationFreshness.accepts(now, now + 1L, 180_000L))
        assertFalse(LocationFreshness.accepts(now, now - 180_000_000_000L, 180_000L))
        assertTrue(LocationFreshness.accepts(now, now - 179_000_000_000L, 180_000L))
    }
}
