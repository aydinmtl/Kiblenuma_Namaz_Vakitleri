package com.aydinmtl.namazvakitleri

import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Collator
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Diyanet İşleri Başkanlığı'nın vakitlerini, aynı verileri anahtarsız sunan
 * ezanvakti.emushaf.net servisinden çeker.
 *
 *   /ulkeler            -> ülkeler   (UlkeID, UlkeAdi)
 *   /sehirler/{ulkeId}  -> şehirler  (SehirID, SehirAdi)
 *   /ilceler/{sehirId}  -> ilçeler   (IlceID, IlceAdi)
 *   /vakitler/{ilceId}  -> bugünden itibaren ~30 günlük vakitler
 *
 * Bu fonksiyonlar ağa bağlanır: ana (arayüz) iş parçacığında çağrılmamalı.
 */
object Diyanet {
    private const val BASE = "https://ezanvakti.emushaf.net"
    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    private val KEYS = listOf("Imsak", "Gunes", "Ogle", "Ikindi", "Aksam", "Yatsi")

    data class Place(val id: String, val name: String)

    fun countries(): List<Place> = places(get("$BASE/ulkeler"), "UlkeID", "UlkeAdi")
    fun cities(countryId: String): List<Place> = places(get("$BASE/sehirler/$countryId"), "SehirID", "SehirAdi")
    fun districts(cityId: String): List<Place> = places(get("$BASE/ilceler/$cityId"), "IlceID", "IlceAdi")

    /** Ham JSON döndürür; önbelleğe olduğu gibi yazılır. */
    fun fetchTimes(districtId: String): String = get("$BASE/vakitler/$districtId")

    fun parseDays(json: String): List<PrayerDay> {
        val out = ArrayList<PrayerDay>()
        val arr = try { JSONArray(json) } catch (e: Exception) { return out }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            try {
                val date = LocalDate.parse(o.getString("MiladiTarihKisa"), DATE)
                val times = KEYS.map { LocalTime.parse(o.getString(it).trim()) }
                out.add(PrayerDay(date, times, o.optString("HicriTarihUzun", "")))
            } catch (e: Exception) {
                // Bozuk bir gün varsa onu atla, diğerlerini kullan.
            }
        }
        return out.sortedBy { it.date.toEpochDay() }
    }

    private fun places(json: String, idKey: String, nameKey: String): List<Place> {
        val arr = JSONArray(json)
        val out = ArrayList<Place>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString(idKey, "")
            val name = o.optString(nameKey, "")
            if (id.isNotEmpty() && name.isNotEmpty()) out.add(Place(id, titleCase(name)))
        }
        val collator = Collator.getInstance(TR)
        return out.sortedWith { a, b -> collator.compare(a.name, b.name) }
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "NamazVakitleri-Android/1.0")
        try {
            val code = c.responseCode
            if (code != 200) throw IOException("Sunucu yanıtı: HTTP $code")
            return c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
