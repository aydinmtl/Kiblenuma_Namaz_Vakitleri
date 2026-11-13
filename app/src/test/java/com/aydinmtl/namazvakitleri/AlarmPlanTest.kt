package com.aydinmtl.namazvakitleri

import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alarm sırası ve geciken alarm kuralları (AlarmPlan). */
class AlarmPlanTest {

    private val ogle = 2 // Öğle

    private fun t(hhmm: String) = LocalDateTime.parse("2026-11-11T$hhmm")

    // İmsak 06:00, Güneş 07:30, Öğle 12:20 ... (yalnızca birkaçı yeterli)
    private val moments = listOf(
        Moment(0, t("06:00")),
        Moment(SUNRISE, t("07:30")),
        Moment(ogle, t("12:20")),
    )

    @Test
    fun hatirlatma_yokken_siradaki_vakit_girisi() {
        val e = AlarmPlan.next(moments, 0, { true }, t("06:30"))!!
        assertFalse(e.reminder)
        assertEquals(t("07:30"), e.fireAt)
    }

    @Test
    fun hatirlatma_vakitten_once_gelir() {
        val e = AlarmPlan.next(moments, 15, { true }, t("08:00"))!!
        assertTrue(e.reminder)
        assertEquals(ogle, e.moment.index)
        assertEquals(t("12:05"), e.fireAt)
    }

    @Test
    fun yalnizca_istenen_vakit_icin_hatirlatma() {
        // Öğle için hatırlatma kapalı: sıradaki olay öğlenin kendisi
        val e = AlarmPlan.next(moments, 15, { it != ogle }, t("08:00"))!!
        assertFalse(e.reminder)
        assertEquals(t("12:20"), e.fireAt)
    }

    @Test
    fun ayni_ana_denk_gelen_hatirlatma_bir_dakika_one_alinir() {
        // Güneş 07:30. Öğle hatırlatması 07:30'a denk gelecek şekilde: 12:20 - 290 dk = 07:30
        val first = AlarmPlan.next(moments, 290, { it == ogle }, t("07:00"))!!
        assertTrue(first.reminder)
        assertEquals(t("07:29"), first.fireAt)
        // Hatırlatmadan sonra güneş girişi de atlanmadan gelir
        val second = AlarmPlan.next(moments, 290, { it == ogle }, first.fireAt)!!
        assertFalse(second.reminder)
        assertEquals(SUNRISE, second.moment.index)
    }

    @Test
    fun son_olaydan_sonra_bos() {
        assertNull(AlarmPlan.next(moments, 0, { true }, t("13:00")))
    }

    private val min = 60_000L

    @Test
    fun geciken_vakit_bildirimi_bir_saate_kadar_gosterilir() {
        val at = 1_000_000_000L
        assertTrue(AlarmPlan.shouldNotify(false, at + 40 * min, at, at))
        assertFalse(AlarmPlan.shouldNotify(false, at + 61 * min, at, at))
    }

    @Test
    fun hatirlatma_vakit_girdiyse_gosterilmez() {
        val at = 1_000_000_000L
        val fire = at - 15 * min
        assertTrue(AlarmPlan.shouldNotify(true, fire + 10 * min, fire, at))
        assertFalse(AlarmPlan.shouldNotify(true, at + 1 * min, fire, at))
    }

    @Test
    fun ezan_on_bes_dakikadan_gec_calmaz() {
        val at = 1_000_000_000L
        assertTrue(AlarmPlan.adhanInTime(at + 2 * min, at))
        assertFalse(AlarmPlan.adhanInTime(at + 20 * min, at))
    }
}
