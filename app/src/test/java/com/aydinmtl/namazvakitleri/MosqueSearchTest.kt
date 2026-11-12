package com.aydinmtl.namazvakitleri

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cami aramasının ayrıştırma ve sıralama kısmı (internet gerekmez, örnek veri kullanılır). */
class MosqueSearchTest {

    // Thônex (Cenevre) civarı
    private val lat = 46.197
    private val lon = 6.198

    @Test
    fun sorgu_uc_tur_kaydi_ister_ve_sinir_koymaz() {
        val q = MosqueSearch.buildQuery(lat, lon, 10000)
        assertTrue(q.contains("\"place_of_worship\""))
        assertTrue(q.contains("muslim|islam"))
        assertTrue(q.contains("\"building\"=\"mosque\""))
        assertTrue(q.contains("around:10000,46.197,6.198"))
        assertTrue(q.endsWith("out center tags;"))
    }

    @Test
    fun farkli_dillerdeki_adlar_okunur() {
        assertEquals("Mosquée de Genève", MosqueSearch.nameOf(JSONObject("""{"name":"Mosquée de Genève"}""")))
        assertEquals("Cenevre Camii", MosqueSearch.nameOf(JSONObject("""{"name":"Mosquée","name:tr":"Cenevre Camii"}""")))
        assertEquals("مسجد", MosqueSearch.nameOf(JSONObject("""{"name:ar":"مسجد"}""")))
        assertEquals("İsimsiz cami / mescit", MosqueSearch.nameOf(JSONObject("{}")))
        assertEquals("İsimsiz cami / mescit", MosqueSearch.nameOf(null))
    }

    @Test
    fun yakindan_uzaga_siralar_tekrarlari_ve_baska_dini_atlar() {
        val json = """
        {"elements":[
          {"type":"way","id":1,"center":{"lat":46.2100,"lon":6.1400},
           "tags":{"amenity":"place_of_worship","religion":"muslim","name":"Mosquée Uzak"}},
          {"type":"node","id":2,"lat":46.2000,"lon":6.2000,
           "tags":{"amenity":"place_of_worship","religion":"muslim","name":"Mosquée Yakın"}},
          {"type":"way","id":3,"center":{"lat":46.2001,"lon":6.2001},"tags":{"building":"mosque"}},
          {"type":"way","id":4,"center":{"lat":46.1990,"lon":6.1990},
           "tags":{"building":"mosque","religion":"christian","name":"Eski cami, şimdi kilise"}},
          {"type":"node","id":5}
        ]}
        """.trimIndent()
        val list = MosqueSearch.parse(json, lat, lon)
        assertEquals(listOf("Mosquée Yakın", "Mosquée Uzak"), list.map { it.name })
        assertTrue(list[0].distanceM < list[1].distanceM)
    }

    @Test
    fun bozuk_cevap_bos_liste() {
        assertTrue(MosqueSearch.parse("<html>Too many requests</html>", lat, lon).isEmpty())
        assertTrue(MosqueSearch.parse("", lat, lon).isEmpty())
    }

    @Test
    fun mesafe_ve_yon_dogru() {
        // Ekvatorda 1 derece boylam ~ 111,2 km, yönü doğu (90°)
        val d = MosqueSearch.distanceM(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_195f, d, 200f)
        assertEquals(90f, MosqueSearch.bearing(0.0, 0.0, 0.0, 1.0), 0.1f)
        assertEquals(0f, MosqueSearch.bearing(0.0, 0.0, 1.0, 0.0), 0.1f)
    }
}
