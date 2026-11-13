package com.aydinmtl.namazvakitleri

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Diyanet servisinden gelen JSON'un ayrıştırılması (internet gerekmez, örnek veri kullanılır). */
class DiyanetParseTest {

    private fun gun(tarih: String, imsak: String = "06:00") = """
        {"MiladiTarihKisa":"$tarih","Imsak":"$imsak","Gunes":"07:30","Ogle":"12:20",
         "Ikindi":"14:50","Aksam":"17:00","Yatsi":"18:25",
         "HicriTarihUzun":"1 Cemaziyelevvel 1448","HicriTarihKisa":"1.5.1448"}
    """.trimIndent()

    @Test
    fun gunleri_okur_ve_tarihe_gore_siralar() {
        val json = "[${gun("12.11.2026")},${gun("11.11.2026")}]"
        val days = Diyanet.parseDays(json)
        assertEquals(2, days.size)
        assertEquals(LocalDate.of(2026, 11, 11), days[0].date)
        assertEquals(LocalTime.of(12, 20), days[0].times[2])
        assertEquals("1.5.1448", days[0].hijriShort)
    }

    @Test
    fun bozuk_gun_atlanir_digerleri_kullanilir() {
        val json = "[${gun("11.11.2026", imsak = "bozuk")},${gun("12.11.2026")}]"
        val days = Diyanet.parseDays(json)
        assertEquals(1, days.size)
        assertEquals(LocalDate.of(2026, 11, 12), days[0].date)
    }

    @Test
    fun json_degilse_bos_liste() {
        assertTrue(Diyanet.parseDays("<html>hata</html>").isEmpty())
        assertTrue(Diyanet.parseDays("").isEmpty())
    }
}
