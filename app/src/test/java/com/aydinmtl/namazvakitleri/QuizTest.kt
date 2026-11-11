package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Aile Halkası: yarışma oturumu kaydı, aile ağacı aşamaları ve aile hedefleri. */
class QuizTest {

    @Test
    fun oturum_kaydedilip_aynen_geri_yuklenir() {
        val s = QuizSession(QuizLevel.MEDIUM, listOf("o001", "o007", "o003"), 1, 1)
        assertEquals(s, QuizSession.fromJson(s.toJson()))
    }

    @Test
    fun bozuk_ya_da_bos_kayit_null() {
        assertNull(QuizSession.fromJson(null))
        assertNull(QuizSession.fromJson(""))
        assertNull(QuizSession.fromJson("{bozuk"))
        assertNull(QuizSession.fromJson("""{"level":"kolay"}"""))
    }

    @Test
    fun bilinmeyen_seviye_kolaya_duser() {
        val s = QuizSession.fromJson("""{"level":"yok","order":["k001"],"index":0,"score":0}""")!!
        assertEquals(QuizLevel.EASY, s.level)
    }

    @Test
    fun bitti_mi() {
        assertFalse(QuizSession(QuizLevel.EASY, listOf("a", "b"), 1, 0).finished)
        assertTrue(QuizSession(QuizLevel.EASY, listOf("a", "b"), 2, 0).finished)
        assertTrue(QuizSession(QuizLevel.EASY, emptyList(), 0, 0).finished)
    }

    @Test
    fun dogru_cevap_ilk_sik() {
        val q = Question("k001", "kolay", "?", listOf("Doğru", "Yanlış 1", "Yanlış 2", "Yanlış 3"), "")
        assertEquals("Doğru", q.correct)
    }

    @Test
    fun agac_asamalari_sinirlarda() {
        assertEquals(0, FamilyTree.stageIndex(0))
        assertEquals(0, FamilyTree.stageIndex(9))
        assertEquals(1, FamilyTree.stageIndex(10))
        assertEquals(5, FamilyTree.stageIndex(274))
        assertEquals(6, FamilyTree.stageIndex(275))
        assertEquals(6, FamilyTree.stageIndex(100_000))
        assertEquals(0, FamilyTree.stageIndex(-5))
    }

    @Test
    fun sonraki_asamaya_kalan() {
        assertEquals(10, FamilyTree.toNext(0))
        assertEquals(1, FamilyTree.toNext(24))
        assertEquals(25, FamilyTree.toNext(25)) // Fidan (25) -> Genç ağaç (50)
        assertNull(FamilyTree.toNext(275))
    }

    @Test
    fun asamalar_artan_sirada() {
        val mins = FamilyTree.STAGES.map { it.minPoints }
        assertEquals(0, mins.first())
        assertEquals(mins.sorted(), mins)
        assertEquals(mins.distinct(), mins)
    }

    @Test
    fun hedefler_kaydedilip_geri_yuklenir() {
        val list = listOf(FamilyGoal("Dondurma yemeye gidiyoruz", 5), FamilyGoal("Piknik", 3))
        assertEquals(list, FamilyGoal.listFromJson(FamilyGoal.listToJson(list)))
        assertTrue(FamilyGoal.listFromJson(null).isEmpty())
        assertTrue(FamilyGoal.listFromJson("bozuk").isEmpty())
    }
}
