package com.aydinmtl.namazvakitleri

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshPolicyTest {
    @Test
    fun exhaustedAndMissingDataNeedRecoveryWithoutAnyPrayerAlarm() {
        val today = LocalDate.of(2026, 11, 14)
        assertTrue(RefreshPolicy.needsData("123", false, null, today))
        assertTrue(RefreshPolicy.needsData("123", false, today.minusDays(1), today))
        assertTrue(RefreshPolicy.needsData("123", false, today.plusDays(9), today))
        assertFalse(RefreshPolicy.needsData("123", false, today.plusDays(10), today))
        assertFalse(RefreshPolicy.needsData(null, false, null, today))
        assertFalse(RefreshPolicy.needsData("hesap", true, null, today))
    }
}
