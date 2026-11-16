package com.aydinmtl.namazvakitleri

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
}
