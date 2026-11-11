package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Soru bankası (assets/sorular.json) ve "bu soruda hata var" işaretleri. */
class QuizBankTest {

    /** Testler app/ klasöründen çalışır; başka yerden çalıştırılırsa depo kökünden de bulunur. */
    private val bank: List<Question> by lazy {
        val f = listOf("src/main/assets/sorular.json", "app/src/main/assets/sorular.json")
            .map { File(it) }.firstOrNull { it.exists() }
            ?: File(System.getProperty("quiz.json") ?: error("sorular.json bulunamadı"))
        QuizBank.parse(f.readText(Charsets.UTF_8))
    }

    @Test
    fun her_sorunun_dort_farkli_sikki_var_ve_kimlikler_tekil() {
        assertEquals(120, bank.size)
        assertEquals(bank.size, bank.map { it.id }.toSet().size)
        for (q in bank) {
            assertEquals(q.id, 4, q.options.size)
            assertEquals(q.id, 4, q.options.toSet().size)
            assertTrue(q.id, q.level in QuizLevel.values().map { it.id })
        }
    }

    @Test
    fun her_seviyede_tur_icin_yeterli_soru_var() {
        for (l in QuizLevel.values()) assertTrue(l.id, bank.count { it.level == l.id } >= ROUND_SIZE)
    }

    @Test
    fun kaynakli_sorular() {
        val src = bank.associate { it.id to it.source }
        assertEquals("Müslim, Zekât 986; Buhârî, Zekât 1511", src["o039"])
        assertEquals("Müslim, Hac 1218; Buhârî, Hac 1739", src["z017"])
        assertEquals("", src["k001"]) // kaynak alanı olmayan soru boş gelir
    }

    @Test
    fun isaretler_json_ve_rapor() {
        val flags = mapOf("o039" to "Not denemesi", "yok999" to "")
        val back = QuizFlags.fromJson(QuizFlags.toJson(flags))
        assertEquals(flags, back)
        val r = QuizFlags.report(back, bank)
        assertTrue(r.contains("[o039]"))
        assertTrue(r.contains("Not: Not denemesi"))
        assertTrue(r.contains("(soru artık bankada yok)"))
        assertFalse(QuizFlags.fromJson("bozuk{").isNotEmpty())
    }
}
