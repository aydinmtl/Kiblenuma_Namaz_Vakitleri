package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Namaz oyununun adımları ve figür duruşları. */
class PrayerGameTest {

    @Test
    fun herAdimdaUcFarkliSikVar() {
        for ((i, s) in PrayerGame.STEPS.withIndex()) {
            assertEquals("adım $i", 3, s.options.size)
            assertEquals("adım $i: şıklar farklı olmalı", 3, s.options.toSet().size)
            assertTrue("adım $i", s.question.isNotBlank() && s.title.isNotBlank() && s.info.isNotBlank())
            assertTrue("adım $i: en az bir hareket", s.moves.isNotEmpty())
        }
    }

    @Test
    fun butunHareketlerinDurusuTanimli() {
        for (s in PrayerGame.STEPS) for (m in s.moves) {
            assertTrue("bilinmeyen duruş: ${m.pose}", Poses.has(m.pose))
            assertTrue(m.ms in 100..30000)
        }
    }

    @Test
    fun herOkumaninSesDosyasiAdiVar() {
        // Her dua/sure için bir ses dosyası adı tanımlı olmalı; adlar res/raw kurallarına uymalı ve birbirinden farklı olmalı
        for (r in Recitations.ALL) assertTrue("ses adı eksik: ${r.title}", PrayerSounds.of(r) != null)
        for (ad in PrayerSounds.ALL) assertTrue("geçersiz dosya adı: $ad", Regex("ses_[a-z0-9_]+").matches(ad))
        assertEquals(PrayerSounds.ALL.size, PrayerSounds.ALL.toSet().size)
        assertEquals(PrayerSounds.TEKBIR, PrayerSounds.ofCaption("Allâhu Ekber"))
        assertEquals(null, PrayerSounds.ofCaption("1"))
    }

    @Test
    fun ucBoyutluSahnedeButunDuruslarVar() {
        // 3B sahnenin duruşları assets/namazoyun3d/duruslar.js içinde "D.ad = ..." diye tanımlanır.
        // Oyundaki her duruşun orada da karşılığı olmalı; yoksa figür o adımda ayakta kalır.
        val dosya = listOf("src/main/assets/namazoyun3d/duruslar.js", "app/src/main/assets/namazoyun3d/duruslar.js")
            .map { java.io.File(it) }.firstOrNull { it.exists() } ?: return
        val js = dosya.readText()
        val tanimli = Regex("""D\.(\w+)\s*=""").findAll(js).map { it.groupValues[1] }.toSet()
        for (s in PrayerGame.STEPS) for (m in s.moves) assertTrue("3B duruşu eksik: ${m.pose}", m.pose in tanimli)
    }

    @Test
    fun abdestleBaslarSelamlaBiter() {
        val steps = PrayerGame.STEPS
        assertEquals(PrayerChapter.ABDEST, steps.first().chapter)
        assertEquals(13, PrayerGame.NAMAZ_START)
        // Abdest bölümünden sonra yalnızca namaz adımları gelir
        assertTrue(steps.drop(PrayerGame.NAMAZ_START).all { it.chapter == PrayerChapter.NAMAZ })
        val last = steps.last().moves.map { it.pose }
        assertTrue("önce sağa sonra sola selam", last.indexOf("selam_r") in 0 until last.indexOf("selam_l"))
    }

    @Test
    fun kaynaklarYalnizcaAyetYaDaHadis() {
        // Kaynak alanına yalnızca Kur'an (sure/ayet) ya da hadis kitabı yazılır
        val ok = Regex("^(Buhârî|Müslim|.+ suresi, [\\d-]+\\. ayet)")
        for (s in PrayerGame.STEPS) if (s.source.isNotEmpty()) assertTrue(s.source, ok.containsMatchIn(s.source))
        for (r in Recitations.ALL) if (r.source.isNotEmpty()) assertTrue(r.source, ok.containsMatchIn(r.source))
    }

    @Test
    fun durakVeSahne() {
        assertEquals("a_sit", PrayerGame.restingPose(0))
        // Abdestin son sorusu abdesthanede, namazın ilk sorusu namaz odasında sorulur
        assertEquals(PrayerChapter.ABDEST, PrayerGame.sceneAt(PrayerGame.NAMAZ_START - 1))
        assertEquals(PrayerChapter.NAMAZ, PrayerGame.sceneAt(PrayerGame.NAMAZ_START))
        assertEquals(PrayerChapter.NAMAZ, PrayerGame.sceneAt(PrayerGame.NAMAZ_START + 1))
        // Namaz başlarken çocuk seccadesini sermiş bekliyordur; sonraki adımda ayakta kıbleye dönüktür
        assertEquals(PrayerGame.NAMAZ_READY_POSE, PrayerGame.restingPose(PrayerGame.NAMAZ_START))
        assertEquals("stand", PrayerGame.restingPose(PrayerGame.NAMAZ_START + 1))
        // Bölüm sınırları: abdest 0..12, namaz 13..son
        assertEquals(0, PrayerGame.chapterStart(PrayerChapter.ABDEST))
        assertEquals(PrayerGame.NAMAZ_START, PrayerGame.chapterEnd(PrayerChapter.ABDEST))
        assertEquals(PrayerGame.NAMAZ_START, PrayerGame.chapterStart(PrayerChapter.NAMAZ))
        assertEquals(PrayerGame.STEPS.size, PrayerGame.chapterEnd(PrayerChapter.NAMAZ))
        assertEquals(Pair(1, 13), PrayerGame.positionInChapter(0))
        assertEquals(Pair(1, PrayerGame.STEPS.size - 13), PrayerGame.positionInChapter(PrayerGame.NAMAZ_START))
    }

    @Test
    fun figurHerDurustaYereBasar() {
        for (name in Poses.names) for (girl in listOf(false, true)) {
            val sk = Skeleton.of(Poses.get(name, girl))
            val pts = sk.points()
            assertTrue(name, pts.all { it.x.isFinite() && it.y.isFinite() })
            val low = maxOf(pts.maxOf { it.y }, sk.head.y + Skeleton.HEAD_R)
            assertEquals("$name yere değmeli", 0f, low, 0.01f)
        }
    }

    @Test
    fun acilarEnKisaYoldanDoner() {
        assertEquals(20f, Pose.shortest(170f, -170f), 0.001f)
        assertEquals(-20f, Pose.shortest(-170f, 170f), 0.001f)
        val a = Poses.get("stand", false).copy(na1 = 170f)
        val b = a.copy(na1 = -170f)
        assertTrue(abs(Pose.shortest(180f, Pose.lerp(a, b, 0.5f).na1)) < 0.01f)
    }

    @Test
    fun abdestSirasiHanefi() {
        // Kulak meshinden sonra ense, ayaklar ondan sonra
        val titles = PrayerGame.STEPS.map { it.title }
        val kulak = titles.indexOf("Kulaklarımızı meshediyoruz")
        val ense = titles.indexOf("Ensemizi meshediyoruz")
        val ayak = titles.indexOf("Sağ ayağımızı yıkıyoruz")
        assertTrue(kulak >= 0 && ense == kulak + 1 && ayak == ense + 1)
    }

    @Test
    fun okumalar() {
        for (r in Recitations.ALL) {
            assertTrue(r.title, r.words.isNotEmpty() && r.meaning.isNotBlank())
            assertTrue(r.durationMs >= 1500)
        }
        // Namazda Fâtiha iki kez (her rekatta), ilk rekatta İhlâs, ikincide Kevser okunur
        val read = PrayerGame.STEPS.flatMap { s -> s.moves.mapNotNull { it.recite } }
        assertEquals(2, read.count { it == Recitations.FATIHA })
        assertEquals(1, read.count { it == Recitations.IHLAS })
        assertEquals(1, read.count { it == Recitations.KEVSER })
        assertEquals(1, read.count { it == Recitations.SUBHANEKE }) // Sübhâneke yalnız ilk rekatta
        assertEquals(4, read.count { it == Recitations.SECDE }) // iki rekatta ikişer secde
        assertEquals(2, read.count { it == Recitations.SELAM })
    }
}
