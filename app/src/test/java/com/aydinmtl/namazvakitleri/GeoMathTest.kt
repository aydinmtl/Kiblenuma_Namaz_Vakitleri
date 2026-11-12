package com.aydinmtl.namazvakitleri

import org.junit.Assert.assertEquals
import org.junit.Test

/** Otomatik konumun kullandığı mesafe ve "şu yöne şu kadar git" hesapları (GeoMath). */
class GeoMathTest {

    @Test
    fun cenevre_annemasse_arasi_yaklasik_7_km() {
        // Cenevre merkez (46.2044, 6.1432) ile Annemasse (46.1944, 6.2376) arası kuş uçuşu ~7,3 km
        assertEquals(7.3, GeoMath.distanceKm(46.2044, 6.1432, 46.1944, 6.2376), 0.3)
    }

    @Test
    fun istanbul_ankara_arasi_yaklasik_350_km() {
        assertEquals(350.0, GeoMath.distanceKm(41.0082, 28.9784, 39.9334, 32.8597), 5.0)
    }

    @Test
    fun ayni_nokta_sifir_km() {
        assertEquals(0.0, GeoMath.distanceKm(46.19, 6.22, 46.19, 6.22), 1e-9)
    }

    @Test
    fun bir_yone_gidince_o_kadar_uzaklasilir() {
        // 8 yönün hepsinde: 11 km gidilen nokta başlangıca 11 km uzakta olmalı.
        for (k in 0 until 8) {
            val p = GeoMath.offset(46.19, 6.22, 11.0, k * 45.0)
            assertEquals(11.0, GeoMath.distanceKm(46.19, 6.22, p.first, p.second), 0.01)
        }
    }

    @Test
    fun kuzeye_gidince_enlem_artar_doguya_gidince_boylam_artar() {
        val north = GeoMath.offset(46.0, 6.0, 111.19, 0.0) // 1 derece enlem ~111,19 km
        assertEquals(47.0, north.first, 0.01)
        assertEquals(6.0, north.second, 0.01)
        val east = GeoMath.offset(0.0, 6.0, 111.19, 90.0) // ekvatorda 1 derece boylam ~111,19 km
        assertEquals(0.0, east.first, 0.01)
        assertEquals(7.0, east.second, 0.01)
    }

    @Test
    fun tarih_cizgisinde_boylam_sarar() {
        val p = GeoMath.offset(0.0, 179.9, 50.0, 90.0)
        assertEquals(-179.65, p.second, 0.05)
    }
}
