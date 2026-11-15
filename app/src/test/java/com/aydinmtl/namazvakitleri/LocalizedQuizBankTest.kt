package com.aydinmtl.namazvakitleri

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizedQuizBankTest {
    private fun asset(name: String) = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        .first { it.exists() }.readText(Charsets.UTF_8)

    @Test
    fun banks_keep_question_identity_and_correct_option_position() {
        val original = QuizBank.parse(asset("sorular.json"))
        for (language in listOf("fr", "en", "de")) {
            val bank = QuizBank.parse(asset(QuizBank.assetName(language)))
            assertEquals(original.map { it.id }, bank.map { it.id })
            bank.zip(original).forEach { (q, tr) ->
                assertEquals(q.id, tr.level, q.level)
                assertEquals(q.id, tr.source, q.source)
                assertEquals(q.id, 4, q.options.toSet().size)
                assertFalse(q.id, q.text.isBlank())
                assertFalse(q.id, q.info.isBlank())
                assertNotEquals(q.id, tr.text, q.text)
                assertNotEquals(q.id, tr.info, q.info)
            }
            assertEquals("5", bank.first { it.id == "k003" }.correct)
            assertEquals("622", bank.first { it.id == "o001" }.correct)
            assertEquals("1071", bank.first { it.id == "z013" }.correct)
        }
    }

    @Test
    fun language_cache_reload_keeps_saved_session_ids() {
        val cache = LocalizedQuizCache()
        var loads = 0
        fun bank(language: String) = cache.get(language) {
            loads++
            QuizBank.parse(asset(QuizBank.assetName(language)))
        }
        val tr = bank("tr")
        val session = QuizSession(QuizLevel.EASY, listOf("k001", "k002"), 1, 1)
        val saved = session.toJson()
        val en = bank("en")
        assertNotEquals(tr[0].text, en[0].text)
        assertSame(tr, bank("tr"))
        assertSame(en, bank("en"))
        assertEquals(2, loads)
        assertEquals(session, QuizSession.fromJson(saved))
        assertTrue(en.any { it.id == session.order[session.index] })
    }

    @Test
    fun unknown_language_uses_turkish_asset() {
        assertEquals("sorular.json", QuizBank.assetName("es"))
        assertEquals("sorular_de.json", QuizBank.assetName("de"))
    }
}
