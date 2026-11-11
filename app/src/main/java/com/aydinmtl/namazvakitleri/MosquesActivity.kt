package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Yakındaki camiler ve mescitler. Veri: OpenStreetMap (Overpass API), ücretsiz ve anahtarsız.
 * Bir camiye dokununca telefonun harita uygulamasında açılır.
 */
class MosquesActivity : Activity() {

    data class Mosque(val name: String, val lat: Double, val lon: Double, val distanceM: Float, val bearing: Float)

    private val items = ArrayList<Mosque>()
    private lateinit var tvSubtitle: TextView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        findViewById<TextView>(R.id.tvTitle).text = "Yakındaki Camiler"
        tvSubtitle = findViewById(R.id.tvSubtitle)
        progress = findViewById(R.id.progress)
        val list = findViewById<ListView>(R.id.list)

        adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_two_line, parent, false)
                val m = items[position]
                v.findViewById<TextView>(R.id.line1).text = m.name
                v.findViewById<TextView>(R.id.line2).text = "Yön: ${direction(m.bearing)} · haritada açmak için dokun"
                v.findViewById<TextView>(R.id.right).text = formatDistance(m.distanceM)
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> openInMaps(items[position]) }

        progress.visibility = View.VISIBLE
        tvSubtitle.text = "Konum bulunuyor…"
        if (LocationHelper.hasPermission(this)) {
            LocationHelper.current(this, maxAgeMs = 5 * 60_000L, timeoutMs = 8_000L) { loc -> start(loc) }
        } else {
            start(null)
        }
    }

    private fun start(loc: Location?) {
        val point = if (loc != null) Pair(loc.latitude, loc.longitude) else Store.placePoint(this)
        if (point == null) {
            progress.visibility = View.GONE
            tvSubtitle.text = "Konum bulunamadı. Ayarlar'dan otomatik konumu aç ya da konum iznini ver."
            return
        }
        val fromGps = loc != null
        tvSubtitle.text = "Camiler aranıyor…"
        Thread {
            val result = try {
                search(point.first, point.second, 10_000)
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                progress.visibility = View.GONE
                if (result == null) {
                    tvSubtitle.text = "Camiler alınamadı. İnternet bağlantını kontrol et."
                    return@runOnUiThread
                }
                items.clear()
                items.addAll(result)
                adapter.notifyDataSetChanged()
                val where = if (fromGps) "bulunduğun yere" else "${Store.placeName(this)} merkezine"
                tvSubtitle.text = if (result.isEmpty()) {
                    "10 km içinde kayıtlı cami bulunamadı."
                } else {
                    "${result.size} yer, $where 10 km içinde. Veri: © OpenStreetMap katkıcıları (eksik olabilir)."
                }
            }
        }.start()
    }

    /** Overpass API'den çevredeki "amenity=place_of_worship + religion=muslim" kayıtları. */
    private fun search(lat: Double, lon: Double, radiusM: Int): List<Mosque> {
        val q = "[out:json][timeout:25];" +
            "nwr[\"amenity\"=\"place_of_worship\"][\"religion\"=\"muslim\"](around:$radiusM,$lat,$lon);" +
            "out center 100;"
        val url = URL("https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(q, "UTF-8"))
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "Kiblenuma-Android/1.0")
        val body = try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            c.disconnect()
        }

        val arr = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<Mosque>()
        val res = FloatArray(2)
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val center = e.optJSONObject("center")
            val la = if (e.has("lat")) e.optDouble("lat") else center?.optDouble("lat") ?: continue
            val lo = if (e.has("lon")) e.optDouble("lon") else center?.optDouble("lon") ?: continue
            val tags = e.optJSONObject("tags")
            val name = tags?.optString("name:tr")?.takeIf { it.isNotBlank() }
                ?: tags?.optString("name")?.takeIf { it.isNotBlank() }
                ?: "İsimsiz cami / mescit"
            Location.distanceBetween(lat, lon, la, lo, res)
            out.add(Mosque(name, la, lo, res[0], (res[1] + 360f) % 360f))
        }
        return out.sortedBy { it.distanceM }
    }

    private fun openInMaps(m: Mosque) {
        val label = Uri.encode(m.name)
        val uri = Uri.parse(String.format(Locale.ROOT, "geo:%f,%f?q=%f,%f(%s)", m.lat, m.lon, m.lat, m.lon, label))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, "Harita uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDistance(m: Float): String =
        if (m < 1000) "${(m / 10).roundToInt() * 10} m" else String.format(Locale.ROOT, "%.1f km", m / 1000).replace('.', ',')

    private fun direction(bearing: Float): String {
        val names = arrayOf("Kuzey", "Kuzeydoğu", "Doğu", "Güneydoğu", "Güney", "Güneybatı", "Batı", "Kuzeybatı")
        return names[(((bearing + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }
}
