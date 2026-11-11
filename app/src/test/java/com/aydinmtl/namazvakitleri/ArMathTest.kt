package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kamerayla kıble ekranındaki açı hesapları (ArMath). */
class ArMathTest {

    @Test
    fun sensor_ve_odaktan_aci() {
        // Kısa kenar 7.3 mm, odak 6.3 mm -> 2*atan(3.65/6.3) ≈ 60.2°
        assertEquals(60.2f, ArMath.cameraFov(7.3f, 6.3f), 0.2f)
    }

    @Test
    fun bilgi_yoksa_varsayilan() {
        assertEquals(ArMath.DEFAULT_CAMERA_FOV, ArMath.cameraFov(0f, 6f), 0f)
    }

    @Test
    fun uzun_ekranda_gorunen_aci_daralir() {
        // 1080x2340 ekran, 3:4 görüntü: yatayın yalnızca ~%61'i görünür
        val v = ArMath.visibleFov(60f, 1080, 2340)
        assertEquals(38.9f, v, 0.5f)
        assertTrue(v < 60f)
    }

    @Test
    fun ekran_3e4_ise_kirpma_yok() {
        assertEquals(60f, ArMath.visibleFov(60f, 1500, 2000), 0.01f)
        // Ekran 3:4'ten genişse yatayda kırpma yok (dikeyde kırpılır)
        assertEquals(60f, ArMath.visibleFov(60f, 2000, 2000), 0.01f)
    }

    @Test
    fun ekran_konumu() {
        val w = 1000
        assertEquals(500f, ArMath.screenX(0f, 20f, w), 0.01f)      // tam ortada
        assertEquals(1000f, ArMath.screenX(20f, 20f, w), 0.01f)    // sağ kenar
        assertEquals(0f, ArMath.screenX(-20f, 20f, w), 0.01f)      // sol kenar
        // Tanjanta göre: 10° kenara göre yarıdan biraz az (0.48)
        assertEquals(500f + 500f * 0.4845f, ArMath.screenX(10f, 20f, w), 1f)
    }
}
