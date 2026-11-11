package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Günün sorusu seçimi ve sesli okuma metni. */
class DailyQuizTest {

    private val ids = (1..120).map { "q%03d".format(it) }

    @Test
    fun ayni_gun_hep_ayni_soru() {
        val d = LocalDate.of(2026, 11, 12)
        assertEquals(QuizDeck.dailyId(ids, d), QuizDeck.dailyId(ids.shuffled(), d))
    }

    @Test
    fun yil_icinde_ilk_120_gun_hep_farkli_soru() {
        val start = LocalDate.of(2027, 1, 1)
        val picked = (0 until 120).map { QuizDeck.dailyId(ids, start.plusDays(it.toLong())) }
        assertEquals(120, picked.toSet().size)
    }

    @Test
    fun soru_yoksa_null() {
        assertNull(QuizDeck.dailyId(emptyList(), LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun okunacak_metinden_isaretler_atilir() {
        val t = QuizSpeech.clean("✓ Doğru!\nHz. Meryem.\n🌱 Ağacımıza +1")
        assertFalse(t.contains("✓"))
        assertFalse(t.contains("🌱"))
        assertTrue(t.startsWith("Doğru!"))
        assertTrue(t.contains("Ağacımıza +1"))
    }

    @Test
    fun soru_ve_siklar_sirayla_okunur() {
        val t = QuizSpeech.question("Peygamberimiz nerede doğdu?", listOf("Medine", "Mekke"))
        assertEquals("Peygamberimiz nerede doğdu?\nBirinci şık: Medine.\nİkinci şık: Mekke.", t)
    }
}
