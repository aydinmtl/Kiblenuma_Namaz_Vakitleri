package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Yarışmadaki sesli okuma metni. */
class QuizSpeechTest {

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
