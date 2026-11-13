package com.aydinmtl.namazvakitleri

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONObject

/**
 * Yakındaki camileri OpenStreetMap'ten (Overpass API) bulan saf mantık.
 * Ekrandan ayrı tutuldu ki bilgisayarda (JVM) test edilebilsin.
 *
 * Not: Arama dilden bağımsızdır. OpenStreetMap'te cami "religion=muslim" gibi
 * İngilizce etiketlerle kayıtlıdır; adı Fransızca, Arapça ya da Türkçe olsa da bulunur.
 * Sorun dil değil, sunucunun yoğunluk yüzünden cevap vermemesiydi.
 */
object MosqueSearch {

    data class Mosque(val name: String, val lat: Double, val lon: Double, val distanceM: Float, val bearing: Float)

    /** Sunucunun cevap veremediği durum (yoğunluk ya da internet yok). */
    class SearchFailed(val busy: Boolean, msg: String) : IOException(msg)

    /**
     * Birbirinin yedeği olan ücretsiz Overpass sunucuları. Biri yoğunsa ("429" ya da "504")
     * sıradakine geçilir. Eskiden yalnızca ilki vardı; o meşgulken liste hiç gelmiyordu.
     */
    val SERVERS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
        "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
    )

    /**
     * Overpass sorgusu. Üç tür kaydı birlikte ister:
     * 1) ibadet yeri + din müslüman (doğru etiket),
     * 2) "religion=islam" diye yanlış girilmiş olanlar,
     * 3) yalnızca "building=mosque" (bina cami olarak çizilmiş, ibadet yeri etiketi unutulmuş).
     * Sonuç sayısı sınırlanmaz: sunucu mesafeye göre kesmediği için sınır en yakınları düşürebilirdi.
     */
    fun buildQuery(lat: Double, lon: Double, radiusM: Int): String {
        val a = "(around:$radiusM,$lat,$lon)"
        return "[out:json][timeout:25];(" +
            "nwr[\"amenity\"=\"place_of_worship\"][\"religion\"~\"^(muslim|islam)$\",i]$a;" +
            "nwr[\"building\"=\"mosque\"]$a;" +
            ");out center tags;"
    }

    /** Sunucu cevabını (JSON) cami listesine çevirir; [lat], [lon] aramanın merkezidir. */
    fun parse(body: String, lat: Double, lon: Double): List<Mosque> {
        val arr = try {
            JSONObject(body).optJSONArray("elements")
        } catch (e: Exception) {
            null
        } ?: return emptyList()
        val out = ArrayList<Mosque>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val center = e.optJSONObject("center")
            val la = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: continue
            val lo = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: continue
            if (la.isNaN() || lo.isNaN()) continue
            val tags = e.optJSONObject("tags")
            // Başka dine ait bir binaysa (ör. eski cami şimdi kilise) atla.
            val religion = tags?.optString("religion").orEmpty().lowercase()
            if (religion.isNotEmpty() && religion != "muslim" && religion != "islam") continue
            out.add(Mosque(nameOf(tags), la, lo, distanceM(lat, lon, la, lo), bearing(lat, lon, la, lo)))
        }
        return dedupe(out).sortedBy { it.distanceM }
    }

    /** Görünecek ad: önce Türkçe, sonra yerel ad, sonra diğer dillerdeki adlar. */
    fun nameOf(tags: JSONObject?): String {
        for (k in listOf("name:tr", "name", "official_name", "name:fr", "name:de", "name:en", "name:ar", "alt_name")) {
            val v = tags?.optString(k)?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return "İsimsiz cami / mescit"
    }

    /**
     * Aynı cami bazen iki kez kayıtlıdır (bina + içindeki nokta). 60 metreden yakın iki kayıttan
     * adı olanı tutar.
     */
    fun dedupe(list: List<Mosque>): List<Mosque> {
        val kept = ArrayList<Mosque>()
        // Adı olanlar önce gelsin ki tekrarlarda onlar kalsın.
        for (m in list.sortedBy { if (it.name.startsWith("İsimsiz")) 1 else 0 }) {
            if (kept.none { distanceM(it.lat, it.lon, m.lat, m.lon) < 60f }) kept.add(m)
        }
        return kept
    }

    /** İki nokta arası mesafe, metre (haversine formülü). */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return (2 * r * atan2(sqrt(h), sqrt(1 - h))).toFloat()
    }

    /** Birinci noktadan ikinciye yön, derece (0 = kuzey, 90 = doğu). */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dl = Math.toRadians(lon2 - lon1)
        val y = sin(dl) * cos(p2)
        val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dl)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }

    /**
     * Sunucuları sırayla dener; ilk cevap verenin sonucunu döndürür.
     * Hepsi başarısızsa [SearchFailed] fırlatır.
     */
    fun search(lat: Double, lon: Double, radiusM: Int): List<Mosque> {
        val form = "data=" + URLEncoder.encode(buildQuery(lat, lon, radiusM), "UTF-8")
        var busy = false
        var last: Exception? = null
        for (server in SERVERS) {
            try {
                return parse(post(server, form), lat, lon)
            } catch (e: SearchFailed) {
                busy = busy || e.busy
                last = e
            } catch (e: Exception) {
                last = e
            }
        }
        throw SearchFailed(busy, last?.message ?: "sunucuya ulaşılamadı")
    }

    /** Overpass'ın önerdiği gibi sorguyu POST ile gönderir (uzun adreslerde GET sorun çıkarabiliyor). */
    private fun post(server: String, form: String): String {
        val c = URL(server).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 30_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
        // Overpass, kim olduğunu belirten bir User-Agent ister.
        c.setRequestProperty("User-Agent", "Kiblenuma-Android/1.0 (github.com/aydinmtl/Kiblenuma_Namaz_Vakitleri)")
        try {
            c.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            if (code != 200) throw SearchFailed(code == 429 || code == 504 || code == 503, "HTTP $code")
            val body = c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            // Overpass zaman aşımında bile 200 dönüp içine "remark" ile hata yazabiliyor.
            if (!body.trimStart().startsWith("{")) throw SearchFailed(true, "beklenmeyen cevap")
            val remark = try { JSONObject(body).optString("remark") } catch (e: Exception) { "" }
            if (remark.contains("runtime error", ignoreCase = true)) throw SearchFailed(true, remark)
            return body
        } finally {
            c.disconnect()
        }
    }
}
