package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** Ülke → Şehir → İlçe seçimi. Seçimden sonra 30 günlük vakitleri indirir. */
class LocationActivity : Activity() {

    private enum class Stage { COUNTRY, CITY, DISTRICT }

    private var stage = Stage.COUNTRY
    private var country: Diyanet.Place? = null
    private var city: Diyanet.Place? = null

    private var all: List<Diyanet.Place> = emptyList()
    private val shown = ArrayList<Diyanet.Place>()
    private lateinit var adapter: ArrayAdapter<String>

    private lateinit var tvTitle: TextView
    private lateinit var etSearch: EditText
    private lateinit var progress: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var list: ListView

    private var lastLoad: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        tvTitle = findViewById(R.id.tvTitle)
        etSearch = findViewById(R.id.etSearch)
        progress = findViewById(R.id.progress)
        tvError = findViewById(R.id.tvError)
        list = findViewById(R.id.list)

        adapter = ArrayAdapter(this, R.layout.item_place, R.id.text, ArrayList<String>())
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            if (position in shown.indices) onPick(shown[position])
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = filter()
        })
        tvError.setOnClickListener { lastLoad?.invoke() }

        showCountries()
    }

    private fun showCountries() {
        stage = Stage.COUNTRY
        load("Ülke seç") { Diyanet.countries() }
    }

    private fun showCities(c: Diyanet.Place) {
        stage = Stage.CITY
        load("${c.name}: şehir seç") { Diyanet.cities(c.id) }
    }

    private fun showDistricts(c: Diyanet.Place) {
        stage = Stage.DISTRICT
        load("${c.name}: ilçe seç") { Diyanet.districts(c.id) }
    }

    private fun load(title: String, loader: () -> List<Diyanet.Place>) {
        lastLoad = { load(title, loader) }
        tvTitle.text = title
        etSearch.setText("")
        all = emptyList()
        filter()
        tvError.visibility = View.GONE
        progress.visibility = View.VISIBLE
        val expected = stage
        Thread {
            try {
                val result = loader()
                runOnUiThread {
                    if (isFinishing || stage != expected) return@runOnUiThread
                    progress.visibility = View.GONE
                    all = result
                    filter()
                }
            } catch (e: Exception) {
                runOnUiThread { showError("Bağlantı hatası (${e.message ?: "bilinmiyor"}). Tekrar denemek için dokun.") }
            }
        }.start()
    }

    private fun showError(msg: String) {
        if (isFinishing) return
        progress.visibility = View.GONE
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun filter() {
        val q = etSearch.text.toString().trim().lowercase(TR)
        shown.clear()
        shown.addAll(if (q.isEmpty()) all else all.filter { it.name.lowercase(TR).contains(q) })
        adapter.clear()
        adapter.addAll(shown.map { it.name })
        adapter.notifyDataSetChanged()
    }

    private fun onPick(p: Diyanet.Place) {
        when (stage) {
            Stage.COUNTRY -> { country = p; showCities(p) }
            Stage.CITY -> { city = p; showDistricts(p) }
            Stage.DISTRICT -> save(p)
        }
    }

    private fun save(district: Diyanet.Place) {
        lastLoad = { save(district) }
        tvError.visibility = View.GONE
        progress.visibility = View.VISIBLE
        list.isEnabled = false
        val label = Diyanet.label(district, city, country)
        val wasAuto = Store.autoLocation(this)
        val app = applicationContext
        Thread {
            try {
                val json = Diyanet.fetchTimes(district.id)
                if (Diyanet.parseDays(json).isEmpty()) throw IllegalStateException("bu yer için vakit bulunamadı")
                Store.saveLocation(app, district.id, label, json)
                // Elle seçilen yer, otomatik konum tarafından ezilmesin.
                Store.setAutoLocation(app, false)
                AlarmScheduler.schedule(app)
                PrayerWidget.updateAll(app)
                runOnUiThread {
                    if (wasAuto) {
                        Toast.makeText(this, "Otomatik konum kapatıldı. Ayarlar'dan tekrar açabilirsin.", Toast.LENGTH_LONG).show()
                    }
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    list.isEnabled = true
                    showError("Vakitler alınamadı (${e.message ?: "bilinmiyor"}). Tekrar denemek için dokun.")
                }
            }
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (stage) {
            Stage.DISTRICT -> country?.let { showCities(it) } ?: showCountries()
            Stage.CITY -> showCountries()
            Stage.COUNTRY -> {
                if (Store.districtId(this) == null) {
                    finishAffinity() // Henüz yer seçilmedi: uygulamadan çık.
                } else {
                    @Suppress("DEPRECATION")
                    super.onBackPressed()
                }
            }
        }
    }
}
