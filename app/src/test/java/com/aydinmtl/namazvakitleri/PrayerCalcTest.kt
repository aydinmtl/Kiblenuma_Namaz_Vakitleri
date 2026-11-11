package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * PrayerCalc'ın astronomik hesabını, bağımsız bir kaynakla (Python "astral" kütüphanesi,
 * NOAA formülleri) üretilmiş değerlerle karşılaştırır. 2 dakikaya kadar fark kabul edilir.
 */
class PrayerCalcTest {

    private val geneva = Pair(46.1925, 6.2192) // Thônex / Cenevre
    private val istanbul = Pair(41.0082, 28.9784)
    private val zurich = ZoneId.of("Europe/Zurich")
    private val istanbulZone = ZoneId.of("Europe/Istanbul")

    /** "05:43" -> günün kaçıncı dakikası */
    private fun min(s: String): Int = LocalTime.parse(s).let { it.hour * 60 + it.minute }
    private fun min(t: LocalTime): Int = t.hour * 60 + t.minute

    private fun assertClose(expected: List<String>, actual: List<LocalTime>, tolerance: Int = 2) {
        assertEquals(6, actual.size)
        for (i in 0..5) {
            val diff = abs(min(expected[i]) - min(actual[i]))
            assertTrue(
                "${PRAYER_NAMES[i]}: beklenen ${expected[i]}, bulunan ${actual[i]} ($diff dk fark)",
                diff <= tolerance,
            )
        }
    }

    private fun assertAscending(t: List<LocalTime>) {
        for (i in 1..5) assertTrue("Vakitler sıralı değil: $t", t[i].isAfter(t[i - 1]))
    }

    @Test
    fun cenevre_ilkbahar_MWL() {
        val t = PrayerCalc.day(LocalDate.of(2026, 3, 20), geneva.first, geneva.second, zurich, CalcMethod.MWL, 1)!!
        // Kaynak (astral): imsak 04:57, güneş 06:39, öğle 12:43, ikindi 16:05, akşam 18:47, yatsı 20:23
        assertClose(listOf("04:57", "06:39", "12:43", "16:05", "18:47", "20:23"), t)
    }

    @Test
    fun cenevre_ikindi_hanefi() {
        val t = PrayerCalc.day(LocalDate.of(2026, 3, 20), geneva.first, geneva.second, zurich, CalcMethod.MWL, 2)!!
        // Hanefi ikindi (gölge boyunun 2 katı): 16:56
        assertEquals(min("16:56").toDouble(), min(t[3]).toDouble(), 2.0)
    }

    @Test
    fun cenevre_kis_MWL() {
        val t = PrayerCalc.day(LocalDate.of(2026, 12, 21), geneva.first, geneva.second, zurich, CalcMethod.MWL, 1)!!
        assertClose(listOf("06:25", "08:15", "12:33", "14:35", "16:51", "18:35"), t)
    }

    @Test
    fun istanbul_kis_MWL() {
        val t = PrayerCalc.day(LocalDate.of(2026, 12, 21), istanbul.first, istanbul.second, istanbulZone, CalcMethod.MWL, 1)!!
        assertClose(listOf("06:46", "08:26", "13:02", "15:22", "17:39", "19:13"), t)
    }

    @Test
    fun istanbul_yaz_MWL() {
        val t = PrayerCalc.day(LocalDate.of(2026, 6, 21), istanbul.first, istanbul.second, istanbulZone, CalcMethod.MWL, 1)!!
        assertClose(listOf("03:24", "05:32", "13:06", "17:07", "20:39", "22:38"), t)
    }

    @Test
    fun cenevre_yaz_yuksekEnlemKurali() {
        // Haziranda Cenevre'de 17° yatsı gece yarısını geçer (00:01). Açı tabanlı yüksek enlem
        // kuralı yatsıyı "akşam + gecenin 17/60'ı" ile sınırlar: yaklaşık 23:50.
        val t = PrayerCalc.day(LocalDate.of(2026, 6, 21), geneva.first, geneva.second, zurich, CalcMethod.MWL, 1)!!
        assertAscending(t)
        assertEquals(min("05:44").toDouble(), min(t[1]).toDouble(), 2.0) // güneş
        assertEquals(min("21:30").toDouble(), min(t[4]).toDouble(), 2.0) // akşam
        assertEquals(min("23:50").toDouble(), min(t[5]).toDouble(), 3.0) // sınırlanmış yatsı
        assertEquals(min("03:15").toDouble(), min(t[0]).toDouble(), 3.0) // sınırlanmış imsak
    }

    @Test
    fun katar_yatsi_aksamdan_90dk_sonra() {
        val t = PrayerCalc.day(LocalDate.of(2026, 3, 20), geneva.first, geneva.second, zurich, CalcMethod.QATAR, 1)!!
        assertEquals(90, min(t[5]) - min(t[4]))
    }

    @Test
    fun kutup_bolgesi_gunes_batmiyorsa_null() {
        // Tromsø, 21 Haziran: güneş hiç batmaz.
        assertNull(PrayerCalc.day(LocalDate.of(2026, 6, 21), 69.65, 18.96, ZoneId.of("Europe/Oslo"), CalcMethod.MWL, 1))
    }

    @Test
    fun butun_yontemler_bir_yil_boyunca_sirali() {
        // Ümmü'l-Kurâ (MAKKAH) Ramazan için Android'in Hicri takvimini kullanır; JVM'de yok, atlanır.
        val methods = CalcMethod.values().filter { it != CalcMethod.MAKKAH }
        val places = listOf(istanbul to istanbulZone, geneva to zurich)
        for (m in methods) for ((p, zone) in places) {
            var d = LocalDate.of(2026, 1, 1)
            while (d.year == 2026) {
                val t = PrayerCalc.day(d, p.first, p.second, zone, m, 1)!!
                assertAscending(t)
                d = d.plusDays(7)
            }
        }
    }

    @Test
    fun bilinmeyen_yontem_kimligi_Diyanete_duser() {
        assertEquals(CalcMethod.DIYANET, CalcMethod.byId(null))
        assertEquals(CalcMethod.DIYANET, CalcMethod.byId("yok-boyle-yontem"))
        assertEquals(CalcMethod.ISNA, CalcMethod.byId("isna"))
    }

    @Test
    fun trondheim_yaz_yatsi_gece_yarisini_gecer_ama_sira_bozulmaz() {
        // Trondheim (Norveç), Haziran: yatsı (yüksek enlem kuralıyla bile) gece yarısını geçer.
        val zone = ZoneId.of("Europe/Oslo")
        val days = (0L..6L).map { i ->
            val date = LocalDate.of(2026, 6, 18).plusDays(i)
            PrayerDay(date, PrayerCalc.day(date, 63.43, 10.39, zone, CalcMethod.MWL, 1)!!, "")
        }
        assertTrue("Yatsı gece yarısını geçmeliydi: ${days[3].times}", days[3].times[5].isBefore(days[3].times[4]))
        val m = Prayers.moments(days)
        // Her gün kendi sırasında: imsak, güneş, öğle, ikindi, akşam, yatsı, (ertesi gün) imsak...
        assertEquals((0 until m.size).map { it % 6 }, m.map { it.index })
    }
}
