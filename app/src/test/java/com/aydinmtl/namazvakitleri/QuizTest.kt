package com.aydinmtl.namazvakitleri

import kotlin.random.Random
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
        assertEquals(0, FamilyTree.stageIndex(4))
        assertEquals(1, FamilyTree.stageIndex(5))
        assertEquals(5, FamilyTree.stageIndex(99))
        assertEquals(6, FamilyTree.stageIndex(100))
        assertEquals(6, FamilyTree.stageIndex(100_000))
        assertEquals(0, FamilyTree.stageIndex(-5))
    }

    @Test
    fun sonraki_asamaya_kalan() {
        assertEquals(5, FamilyTree.toNext(0))
        assertEquals(6, FamilyTree.toNext(24)) // Fidan (15) -> Genç ağaç (30)
        assertEquals(5, FamilyTree.toNext(25))
        assertNull(FamilyTree.toNext(100))
    }

    @Test
    fun son_asamaya_soru_bankasiyla_ulasilabilir() {
        // 120 soru var; her soru en fazla 1 puan verdiği için son aşama 120'yi geçmemeli.
        assertTrue(FamilyTree.STAGES.last().minPoints <= 120)
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

    // ---- 10 soruluk turlar (QuizDeck) ----

    private val ids = (1..40).map { "k%03d".format(it) }

    @Test
    fun tur_10_soru_ve_tekrarsiz() {
        val (round, seen) = QuizDeck.nextRound(ids, emptySet(), random = Random(1))
        assertEquals(10, round.size)
        assertEquals(10, round.distinct().size)
        assertEquals(round.toSet(), seen)
    }

    @Test
    fun dort_turda_40_sorunun_hepsi_birer_kez_gelir() {
        var seen = emptySet<String>()
        val asked = ArrayList<String>()
        repeat(4) {
            val (round, s) = QuizDeck.nextRound(ids, seen, random = Random(it))
            asked.addAll(round)
            seen = s
        }
        assertEquals(ids.toSet(), asked.toSet())
        assertEquals(40, asked.size)
    }

    @Test
    fun deste_bitince_yeniden_karisir() {
        // 35 soru görülmüş: 5'i kalanlardan, 5'i yeni desteden gelir; tur içinde tekrar yok.
        val seen = ids.take(35).toSet()
        val (round, newSeen) = QuizDeck.nextRound(ids, seen, random = Random(7))
        assertEquals(10, round.size)
        assertEquals(10, round.distinct().size)
        assertTrue(round.containsAll(ids.drop(35)))
        // Yeni destede yalnızca ikinci kısımdan gelenler "görülmüş" sayılır
        assertEquals(5, newSeen.size)
        assertTrue(newSeen.all { it in ids.take(35) })
    }

    @Test
    fun bankadan_cikan_soru_unutulur_ve_az_soruda_tur_kisalir() {
        val (round, _) = QuizDeck.nextRound(listOf("a", "b", "c"), setOf("silinmis"), random = Random(3))
        assertEquals(setOf("a", "b", "c"), round.toSet())
        assertEquals(3, round.size)
    }

    @Test
    fun oyuncu_adlari_ayrilir() {
        assertEquals(listOf("Annem", "Babam", "Zeynep"), QuizDeck.parsePlayers(" Annem, Babam ,Zeynep, ,Annem"))
        assertTrue(QuizDeck.parsePlayers("  ").isEmpty())
        assertEquals(8, QuizDeck.parsePlayers((1..12).joinToString(",") { "K$it" }).size)
    }

    // ---- Sırayla oynama ----

    @Test
    fun oyuncular_sirayla_doner_ve_puanlar_kisiye_yazilir() {
        var s = QuizSession(QuizLevel.EASY, listOf("a", "b", "c", "d", "e"), 0, 0, listOf("Annem", "Zeynep"))
        assertEquals(0, s.currentPlayer)
        s = s.answered(true)   // Annem doğru
        assertEquals(1, s.currentPlayer)
        s = s.answered(false)  // Zeynep yanlış
        s = s.answered(true)   // Annem doğru
        s = s.answered(true)   // Zeynep doğru
        assertEquals(listOf(2, 1), s.scores)
        assertEquals(3, s.score)
        assertEquals(4, s.index)
    }

    @Test
    fun tek_kisilik_oyunda_oyuncu_yok() {
        val s = QuizSession(QuizLevel.EASY, listOf("a"), 0, 0)
        assertNull(s.currentPlayer)
        assertEquals(1, s.answered(true).score)
        assertTrue(s.answered(true).scores.isEmpty())
    }

    @Test
    fun oyunculu_ve_tekrar_turu_kaydedilip_yuklenir() {
        val s = QuizSession(QuizLevel.HARD, listOf("z001", "z002"), 1, 1, listOf("Annem", "Babam"), listOf(1, 0), review = true)
        assertEquals(s, QuizSession.fromJson(s.toJson()))
    }

    @Test
    fun eski_surum_kaydi_tek_kisilik_olarak_yuklenir() {
        val s = QuizSession.fromJson("""{"level":"orta","order":["o001","o002"],"index":1,"score":1}""")!!
        assertTrue(s.players.isEmpty())
        assertFalse(s.review)
        assertEquals(1, s.score)
    }
}
