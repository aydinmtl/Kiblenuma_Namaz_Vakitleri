package com.aydinmtl.namazvakitleri

import android.annotation.SuppressLint
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
import java.text.Collator

/** Ülke → Şehir → İlçe seçimi. Seçimden sonra 30 günlük vakitleri indirir. */
class LocationActivity : LangActivity() {

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

    private var manualRequest: Long? = null

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
            tv.text = getString(R.string.loc_auto_failed, reason)
            tv.visibility = View.VISIBLE
        }

        showCountries()
    }

    private fun showCountries() {
        stage = Stage.COUNTRY
        load(getString(R.string.loc_pick_country)) { Diyanet.countries() }
    }

    private fun showCities(c: Diyanet.Place) {
        stage = Stage.CITY
        load(getString(R.string.loc_pick_city, displayName(c, Stage.COUNTRY))) { Diyanet.cities(c.id) }
    }

    private fun showDistricts(c: Diyanet.Place) {
        stage = Stage.DISTRICT
        load(getString(R.string.loc_pick_district, displayName(c, Stage.CITY))) { Diyanet.districts(c.id) }
    }

    /**
     * Listede görünen ad. Türkçede Diyanet'in adı aynen yazılır. Öteki dillerde ülke adları seçili
     * dilde yazılır ("Almanya" yerine "Allemagne" / "Germany" / "Deutschland"); yoksa Fransızca
     * kullanan biri ülkesini listede bulamaz. Şehir ve ilçe adları Diyanet'in verdiği gibidir
     * (Diyanet bunları tek biçimde verir); seçimden sonra ekrandaki ad ayrıca çevrilir (PlaceLabels).
     */
    private fun displayName(p: Diyanet.Place, at: Stage = stage): String {
        if (Lang.code(this) == "tr") return p.name
        return when (at) {
            Stage.COUNTRY ->
                CountryNames.display(CountryNames.code(p.id), Lang.locale(this))
                    ?: if (p.nameEn.isNotBlank()) titleCase(p.nameEn, turkish = false) else p.name
            // Bazı ülkelerin tek "şehri" ülkenin kendisidir (Fransa → "Fransa"): o da ülke adıyla yazılır.
            Stage.CITY ->
                country?.takeIf { norm(it.name) == norm(p.name) }?.let { displayName(it, Stage.COUNTRY) } ?: p.name
            Stage.DISTRICT -> p.name
        }
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
                    // Liste Türkçe adlara göre sıralı gelir; adlar başka dilde gösteriliyorsa ona göre sırala.
                    all = if (Lang.code(this) == "tr") {
                        result
                    } else {
                        val collator = Collator.getInstance(Lang.locale(this))
                        result.sortedWith { a, b -> collator.compare(displayName(a, expected), displayName(b, expected)) }
                    }
                    filter()
                }
            } catch (e: Exception) {
                runOnUiThread { showError(getString(R.string.loc_conn_error, Ui.errorText(this, e))) }
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
        // norm() harfleri sadeleştirir: "kadikoy" yazınca "Kadıköy", "zurih" yazınca "Zürih" bulunur.
        val q = norm(etSearch.text.toString())
        shown.clear()
        // Görünen ada, Diyanet'in Türkçe adına ve İngilizce adına bakılır: "suisse", "isvicre" ve
        // "switzerland" yazan da aynı ülkeyi bulur.
        shown.addAll(
            if (q.isEmpty()) {
                all
            } else {
                all.filter { norm(displayName(it)).contains(q) || norm(it.name).contains(q) || norm(it.nameEn).contains(q) }
            },
        )
        adapter.clear()
        adapter.addAll(shown.map { displayName(it) })
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
        val request = Store.beginManualSelection()
        manualRequest = request
        val selectedCity = city
        val selectedCountry = country
        val label = Diyanet.label(district, selectedCity, selectedCountry)
        val fromAutoFailure = intent.hasExtra(EXTRA_REASON)
        val wasAuto = Store.autoLocation(this) && !fromAutoFailure
        val app = applicationContext
        Thread {
            try {
                val json = Diyanet.fetchTimes(district.id)
                if (Diyanet.parseDays(json).isEmpty()) throw UserMessage(getString(R.string.loc_no_times))
                // Koordinat: hesaplama yöntemleri, kıble ve camiler için.
                val c = selectedCity
                val k = selectedCountry
                val point = AutoLocation.geocodeName(app, listOf(
                    listOfNotNull(district.name, c?.name, k?.name).joinToString(", "),
                    listOf(district.nameEn, c?.nameEn ?: "", k?.nameEn ?: "").filter { it.isNotBlank() }.joinToString(", "),
                    label,
                ))
                // Elle seçilen yer otomatik konum tarafından ezilmesin. Ama bu ekran otomatik konum
                // başarısız olduğu için açıldıysa otomatik konum açık kalır (yolculukta yine çalışsın).
                val accepted = Store.finishManualSelection(request) {
                    Store.saveLocation(
                        app, district.id, label, json, point,
                        CountryNames.code(k?.id), Diyanet.labelEndsWithCountry(district, k),
                    )
                    if (!fromAutoFailure) Store.setAutoLocation(app, false)
                }
                if (!accepted) {
                    runOnUiThread {
                        if (manualRequest == request && !isFinishing && !isDestroyed) {
                            manualRequest = null
                            list.isEnabled = true
                            showError(getString(R.string.loc_selection_cancelled))
                        }
                    }
                    return@Thread
                }
                AlarmScheduler.schedule(app)
                PrayerWidget.updateAll(app)
                runOnUiThread {
                    if (manualRequest != request || isFinishing || isDestroyed) return@runOnUiThread
                    manualRequest = null
                    if (wasAuto) {
                        Toast.makeText(this, getString(R.string.loc_auto_turned_off), Toast.LENGTH_LONG).show()
                    }
                    finish()
                }
            } catch (e: Exception) {
                Store.cancelManualSelection(request)
                runOnUiThread {
                    if (manualRequest != request || isFinishing || isDestroyed) return@runOnUiThread
                    manualRequest = null
                    list.isEnabled = true
                    showError(getString(R.string.loc_times_failed, Ui.errorText(this, e)))
                }
            }
        }.start()
    }

    private fun cancelSelection() {
        manualRequest?.let(Store::cancelManualSelection)
        manualRequest = null
        list.isEnabled = true
    }

    override fun onDestroy() {
        cancelSelection()
        super.onDestroy()
    }

    // Android 12 ve öncesinde geri tuşu buraya gelir.
    // Lint "geri hareketi artık onBackPressed'i çağırmıyor" diye uyarır. Doğru: Android 13+ için
    // geri hareketi onCreate'te OnBackInvokedCallback ile kayıtlıdır; bu metot eski sürümler içindir.
    @SuppressLint("GestureBackNavigation")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goBack()

    /** Geri: ilçe listesinden şehre, şehirden ülkeye, ülke listesinden çıkış. */
    private fun goBack() {
        cancelSelection()
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
