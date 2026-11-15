package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sunum çevirisi, kayıtlı adımları ve Arapça okumaları değiştirmemeli. */
class LocalizedPrayerContentTest {
    @Test
    fun animation_intention_and_greetings_follow_selected_language() {
        val intention = Recitations.NIYET.copy(
            title = "Intention",
            text = "I intend to perform the dawn prayer for Allah.",
            meaning = "Intention is made in the heart.",
        )
        val originals = PrayerGame.STEPS.flatMap { it.moves }
        val translated = PrayerGame.presentMoves(originals, intention, "Right greeting", "Left greeting")
        assertEquals(originals.size, translated.size)
        originals.zip(translated).forEach { (original, shown) ->
            assertEquals(original.pose, shown.pose)
            assertEquals(original.ms, shown.ms)
            assertEquals(original.water, shown.water)
            if (original.recite == Recitations.NIYET) {
                assertEquals(intention, shown.recite)
                assertNotEquals(original.recite?.text, shown.recite?.text)
            } else {
                assertEquals(original.recite, shown.recite)
            }
        }
        assertTrue(translated.any { it.caption == "Right greeting" })
        assertTrue(translated.any { it.caption == "Left greeting" })
        assertEquals("Niyet ettim Allah rızası için bugünkü sabah namazının farzını kılmaya.", Recitations.NIYET.text)
        assertEquals("kolay", QuizLevel.EASY.id)
        assertEquals(13, PrayerGame.NAMAZ_START)
    }
}
