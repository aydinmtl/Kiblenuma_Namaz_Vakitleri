package com.aydinmtl.namazvakitleri

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
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Yakındaki camiler ve mescitler. Veri: OpenStreetMap (Overpass API), ücretsiz ve anahtarsız.
 * Arama ve ayrıştırma MosqueSearch.kt dosyasında.
 * Bir camiye dokununca telefonun harita uygulamasında açılır.
 */
class MosquesActivity : LangActivity() {

    private companion object {
        /** Listede en çok bu kadar yer gösterilir (en yakınlar). */
        const val MAX_SHOWN = 150
    }

    private val items = ArrayList<MosqueSearch.Mosque>()
    private lateinit var tvSubtitle: TextView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: BaseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        findViewById<TextView>(R.id.tvTitle).text = getString(R.string.mq_title)
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
                v.findViewById<TextView>(R.id.line1).text = if (m.name == MosqueSearch.UNNAMED) getString(R.string.mq_unnamed) else m.name
                v.findViewById<TextView>(R.id.line2).text = getString(R.string.mq_direction, direction(m.bearing))
                v.findViewById<TextView>(R.id.right).text = formatDistance(m.distanceM)
                return v
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ -> openInMaps(items[position]) }

        progress.visibility = View.VISIBLE
        tvSubtitle.text = getString(R.string.mq_finding)
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
            tvSubtitle.text = getString(R.string.mq_no_location)
            return
        }
        val fromGps = loc != null
        progress.visibility = View.VISIBLE
        tvSubtitle.setOnClickListener(null)
        tvSubtitle.text = getString(R.string.mq_searching)
        Thread {
            // Tek seferde 10 km içinde ara, telefonda mesafeye göre sırala.
            // (Eskiden önce 3 km, az çıkarsa 10 km diye iki istek gidiyordu; az camili yerlerde,
            // örneğin Cenevre'de, ikinci istek sunucuyu daha çabuk "yoğun" durumuna düşürüyordu.)
            val radiusKm = 10
            var error: MosqueSearch.SearchFailed? = null
            val result = try {
                MosqueSearch.search(point.first, point.second, radiusKm * 1000).take(MAX_SHOWN)
            } catch (e: MosqueSearch.SearchFailed) {
                error = e
                null
            } catch (e: Exception) {
                error = MosqueSearch.SearchFailed(false, e.message ?: "")
                null
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                progress.visibility = View.GONE
                if (result == null) {
                    tvSubtitle.text = if (error?.busy == true) {
                        getString(R.string.mq_busy)
                    } else {
                        getString(R.string.mq_failed)
                    }
                    // Yazıya dokununca aramayı yeniden başlat.
                    tvSubtitle.setOnClickListener { start(loc) }
                    return@runOnUiThread
                }
                items.clear()
                items.addAll(result)
                adapter.notifyDataSetChanged()
                val where = if (fromGps) getString(R.string.mq_where_gps) else getString(R.string.mq_where_place, Store.placeName(this))
                tvSubtitle.text = if (result.isEmpty()) {
                    getString(R.string.mq_none, radiusKm)
                } else {
                    getString(R.string.mq_found, result.size, where, radiusKm)
                }
            }
        }.start()
    }

    private fun openInMaps(m: MosqueSearch.Mosque) {
        val label = Uri.encode(m.name)
        val uri = Uri.parse(String.format(Locale.ROOT, "geo:%f,%f?q=%f,%f(%s)", m.lat, m.lon, m.lat, m.lon, label))
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.mq_no_maps), Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatDistance(m: Float): String =
        if (m < 1000) "${(m / 10).roundToInt() * 10} m" else String.format(Lang.locale(this), "%.1f km", m / 1000)

    private fun direction(bearing: Float): String {
        val names = resources.getStringArray(R.array.mq_directions)
        return names[(((bearing + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
    }
}
