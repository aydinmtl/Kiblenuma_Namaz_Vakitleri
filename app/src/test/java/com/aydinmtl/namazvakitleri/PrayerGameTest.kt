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
            assertTrue(m.ms in 100..5000)
        }
    }

    @Test
    fun abdestleBaslarSelamlaBiter() {
        val steps = PrayerGame.STEPS
        assertEquals(PrayerChapter.ABDEST, steps.first().chapter)
        assertEquals(12, PrayerGame.NAMAZ_START)
        // Abdest bölümünden sonra yalnızca namaz adımları gelir
        assertTrue(steps.drop(PrayerGame.NAMAZ_START).all { it.chapter == PrayerChapter.NAMAZ })
        val last = steps.last().moves.map { it.pose }
        assertTrue("önce sağa sonra sola selam", last.indexOf("selam_r") in 0 until last.indexOf("selam_l"))
    }

    @Test
    fun kaynaklarYalnizcaAyetYaDaHadis() {
        // Kaynak alanına yalnızca Kur'an (sure/ayet) ya da hadis kitabı yazılır
        val ok = Regex("^(Buhârî|Müslim|.+ suresi, \\d+\\. ayet)")
        for (s in PrayerGame.STEPS) if (s.source.isNotEmpty()) assertTrue(s.source, ok.containsMatchIn(s.source))
    }

    @Test
    fun durakVeSahne() {
        assertEquals("a_sit", PrayerGame.restingPose(0))
        assertEquals(PrayerChapter.ABDEST, PrayerGame.sceneAt(PrayerGame.NAMAZ_START))
        assertEquals(PrayerChapter.NAMAZ, PrayerGame.sceneAt(PrayerGame.NAMAZ_START + 1))
        assertEquals(Pair(1, 12), PrayerGame.positionInChapter(0))
        assertEquals(Pair(1, PrayerGame.STEPS.size - 12), PrayerGame.positionInChapter(PrayerGame.NAMAZ_START))
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
}
