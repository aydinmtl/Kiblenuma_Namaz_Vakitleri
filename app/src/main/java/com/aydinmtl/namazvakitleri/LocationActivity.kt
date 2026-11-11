package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.os.Build
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
import android.window.OnBackInvokedDispatcher

/** Ülke → Şehir → İlçe seçimi. Seçimden sonra 30 günlük vakitleri indirir. */
class LocationActivity : Activity() {

    companion object {
        const val EXTRA_REASON = "reason"
    }

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
        // Geri hareketi / geri tuşu. Android 13+ yeni yolu kullanır (hedef API 36'da
        // eski onBackPressed artık çağrılmaz); eskiler aşağıdaki onBackPressed'i kullanır.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { goBack() }
        }
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

        // Ana ekran otomatik konumda başarısız olduysa nedenini göster.
        intent.getStringExtra(EXTRA_REASON)?.let { reason ->
            val tv = findViewById<TextView>(R.id.tvReason)
            tv.text = "Konumun otomatik bulunamadı: $reason\nŞimdilik şehrini elle seçebilirsin."
            tv.visibility = View.VISIBLE
        }

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
        val fromAutoFailure = intent.hasExtra(EXTRA_REASON)
        val wasAuto = Store.autoLocation(this) && !fromAutoFailure
        val app = applicationContext
        Thread {
            try {
                val json = Diyanet.fetchTimes(district.id)
                if (Diyanet.parseDays(json).isEmpty()) throw IllegalStateException("bu yer için vakit bulunamadı")
                Store.saveLocation(app, district.id, label, json)
                // Koordinat: hesaplama yöntemleri, kıble ve camiler için.
                val c = city
                val k = country
                Store.savePlacePoint(app, AutoLocation.geocodeName(app, listOf(
                    listOfNotNull(district.name, c?.name, k?.name).joinToString(", "),
                    listOf(district.nameEn, c?.nameEn ?: "", k?.nameEn ?: "").filter { it.isNotBlank() }.joinToString(", "),
                    label,
                )))
                // Elle seçilen yer otomatik konum tarafından ezilmesin. Ama bu ekran otomatik konum
                // başarısız olduğu için açıldıysa otomatik konum açık kalır (yolculukta yine çalışsın).
                if (!fromAutoFailure) Store.setAutoLocation(app, false)
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

    // Android 12 ve öncesinde geri tuşu buraya gelir.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goBack()

    /** Geri: ilçe listesinden şehre, şehirden ülkeye, ülke listesinden çıkış. */
    private fun goBack() {
        when (stage) {
            Stage.DISTRICT -> country?.let { showCities(it) } ?: showCountries()
            Stage.CITY -> showCountries()
            Stage.COUNTRY -> {
                if (Store.districtId(this) == null) {
                    finishAffinity() // Henüz yer seçilmedi: uygulamadan çık.
                } else {
                    finish()
                }
            }
        }
    }
}
