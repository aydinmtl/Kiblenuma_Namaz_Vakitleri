package com.aydinmtl.namazvakitleri

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : LangActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvPlace: TextView
    private lateinit var tvLocMode: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvHijri: TextView
    private lateinit var tvNextLabel: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvStatus: TextView
    private val rowViews = ArrayList<View>()

    private var shownNext: LocalDateTime? = null
    private var shownDate: LocalDate? = null
    private var refreshing = false
    private var checkingLocation = false
    private var locationNote: String? = null
    private var dialogOpen = false

    // Tarih seçili dilde yazılır: "13 Kasım 2026, Cuma" / "13 novembre 2026, vendredi"
    private val dateFormat by lazy { DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Lang.locale(this)) }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Ui.fitSystemBars(this, findViewById(R.id.root))
        Notifications.createChannel(this)

        tvPlace = findViewById(R.id.tvPlace)
        tvLocMode = findViewById(R.id.tvLocMode)
        tvDate = findViewById(R.id.tvDate)
        tvHijri = findViewById(R.id.tvHijri)
        tvNextLabel = findViewById(R.id.tvNextLabel)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvStatus = findViewById(R.id.tvStatus)

        val rows = findViewById<LinearLayout>(R.id.rows)
        // Vakit adları seçili dilde (res/values*/strings.xml içindeki "prayer_names" listesi)
        for (name in resources.getStringArray(R.array.prayer_names)) {
            val row = layoutInflater.inflate(R.layout.item_prayer, rows, false)
            row.findViewById<TextView>(R.id.name).text = name
            rows.addView(row)
            rowViews.add(row)
        }

        tvPlace.setOnClickListener { startActivity(Intent(this, LocationActivity::class.java)) }
        tvLocMode.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        // ↻ düğmesi: widget'taki yenile düğmesiyle aynı pencereyi açar. O pencere konumu
        // (otomatik konum açıksa) yeniden bulur ve vakitleri Diyanet'ten indirir. Pencere
        // kapanınca bu ekran yeniden öne gelir (onResume) ve yeni yer ile vakitler gösterilir.
        findViewById<View>(R.id.btnRefresh).setOnClickListener {
            startActivity(Intent(this, RefreshActivity::class.java))
        }
        // Dil düğmesi: seçili dilin kısaltmasını gösterir, dokununca öteki diller açılır.
        val btnLang = findViewById<TextView>(R.id.btnLang)
        btnLang.text = Lang.code(this).uppercase(java.util.Locale.ROOT)
        btnLang.setOnClickListener { showLanguageMenu(it) }
        findViewById<View>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnImsakiye).setOnClickListener {
            startActivity(Intent(this, ImsakiyeActivity::class.java))
        }
        findViewById<Button>(R.id.btnQibla).setOnClickListener {
            startActivity(Intent(this, QiblaActivity::class.java))
        }
        findViewById<Button>(R.id.btnMosques).setOnClickListener {
            startActivity(Intent(this, MosquesActivity::class.java))
        }
        findViewById<Button>(R.id.btnHolyDays).setOnClickListener {
            startActivity(Intent(this, HolyDaysActivity::class.java))
        }
        findViewById<Button>(R.id.btnKaza).setOnClickListener {
            startActivity(Intent(this, KazaActivity::class.java))
        }
        findViewById<Button>(R.id.btnZikir).setOnClickListener {
            startActivity(Intent(this, ZikirActivity::class.java))
        }
        findViewById<Button>(R.id.btnQuiz).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java))
        }
        findViewById<Button>(R.id.btnPrayerGame).setOnClickListener {
            startActivity(Intent(this, PrayerGameActivity::class.java))
        }
        findViewById<Button>(R.id.btnNamaz3d).setOnClickListener {
            startActivity(Intent(this, Namaz3dActivity::class.java))
        }
        findViewById<Button>(R.id.btnDaily).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java).putExtra(QuizActivity.EXTRA_DAILY, true))
        }
    }

    /** "Günün sorusu" düğmesi: bugün cevaplandıysa ✓ işareti. */
    private fun updateDailyButton() {
        val done = Store.dailyDoneDate(this) == LocalDate.now().toString()
        findViewById<Button>(R.id.btnDaily).setText(if (done) R.string.btn_daily_done else R.string.btn_daily)
    }

    override fun onResume() {
        super.onResume()
        updateDailyButton()
        resumeFlow()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    /** Açılışta ne yapılacağına karar verir: izin açıklaması, konum kontrolü, vakitler. */
    private fun resumeFlow() {
        if (dialogOpen) return

        // 1) İlk açılışta (ve bu güncellemeyle birlikte bir kez) konum izninin nedenini anlat.
        if (!Store.locationIntroShown(this)) {
            dialogOpen = true
            LocationConsent.show(
                this,
                onReady = { dialogOpen = false; resumeFlow() },
                onManual = {
                    dialogOpen = false
                    if (Store.districtId(this) == null) startActivity(Intent(this, LocationActivity::class.java))
                    else resumeFlow()
                },
            )
            return
        }

        val hasDistrict = Store.districtId(this) != null
        val auto = Store.autoLocation(this) && LocationHelper.hasPermission(this)

        // 2) Ne yer seçilmiş ne de otomatik konum var: elle seçim ekranına git.
        if (!hasDistrict && !auto) {
            startActivity(Intent(this, LocationActivity::class.java))
            return
        }

        // 3) Otomatik konum açıksa yer değişmiş mi bak.
        if (auto) checkLocation()

        if (hasDistrict) {
            showTimes()
            if (Store.notificationsOn(this)) askNotificationPermission()
        } else {
            tvPlace.text = getString(R.string.place_searching)
            tvDate.text = LocalDate.now().format(dateFormat)
            tvNextLabel.text = getString(R.string.place_finding)
        }
        updateLocMode()
        updateStatus()
    }

    private fun showTimes() {
        render()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        AlarmScheduler.schedule(this)
        PrayerWidget.updateAll(this)
        maybeRefresh()
    }

    // ---------------- Otomatik konum ----------------

    private fun checkLocation() {
        if (checkingLocation) return
        if (!LocationHelper.isLocationOn(this)) {
            locationNote = getString(R.string.loc_service_off_note)
            noPlaceFallback(getString(R.string.loc_service_off_reason))
            return
        }
        checkingLocation = true
        updateLocMode()
        // En çok 3 dakikalık kayıtlı konum kabul edilir (eskiden 10 dakikaydı). Yolda giderken
        // 10 dakika önceki konum eski şehri gösterebilir; daha eskiyse telefondan yenisi istenir.
        LocationHelper.current(this, maxAgeMs = 3 * 60_000L, timeoutMs = 10_000L) { loc ->
            if (isFinishing || isDestroyed) return@current
            if (loc == null) {
                checkingLocation = false
                locationNote = getString(R.string.loc_unavailable_note)
                updateLocMode()
                noPlaceFallback(getString(R.string.loc_no_report_reason))
                return@current
            }
            if (!AutoLocation.needsCheck(this, loc)) {
                checkingLocation = false
                locationNote = null
                updateLocMode()
                return@current
            }
            val app = applicationContext
            Thread {
                val result = try {
                    AutoLocation.resolve(app, loc) { msg ->
                        // İlk kurulumda hangi adımda olunduğunu göster
                        runOnUiThread { if (Store.districtId(app) == null) tvNextLabel.text = msg }
                    }
                } catch (e: Exception) {
                    AutoLocation.Result.Failed(Lang.str(app, R.string.no_internet))
                }
                runOnUiThread { onLocationResult(result, loc) }
            }.start()
        }
    }

    private fun onLocationResult(r: AutoLocation.Result, loc: android.location.Location) {
        checkingLocation = false
        if (isFinishing || isDestroyed) return
        when (r) {
            is AutoLocation.Result.Changed -> {
                locationNote = null
                AutoLocation.lastFailAt = 0L
                Toast.makeText(this, getString(R.string.loc_updated, r.label), Toast.LENGTH_LONG).show()
                shownNext = null
                showTimes()
                // İlk kurulumda yer ancak şimdi bulundu: bildirim iznini şimdi sor.
                if (Store.notificationsOn(this)) askNotificationPermission()
                updateStatus()
            }
            is AutoLocation.Result.Same -> {
                locationNote = null
                AutoLocation.lastFailAt = 0L
                // Yer aynı kalsa da adının yazımı düzeltilmiş olabilir: ekranı ve widget'ı tazele.
                if (Store.districtId(this) != null) {
                    render()
                    PrayerWidget.updateAll(this)
                }
            }
            is AutoLocation.Result.Failed -> {
                locationNote = getString(R.string.loc_failed_last_chosen, r.reason)
                // ÖNEMLİ: Burada eskiden konum "kontrol edildi" diye kaydediliyordu. Bu yüzden tek bir
                // başarısız denemeden sonra uygulama bir daha hiç denemiyor ve yanlış şehirde kalıyordu.
                // Artık kaydedilmiyor: bir dakika sonra, uygulama yeniden açıldığında tekrar denenir.
                AutoLocation.lastFailAt = System.currentTimeMillis()
                noPlaceFallback(r.reason)
            }
        }
        updateLocMode()
    }

    /** Hiç yer seçilmemişken konum bulunamazsa elle seçim ekranını aç. */
    private fun noPlaceFallback(reason: String) {
        if (Store.districtId(this) != null) return
        startActivity(Intent(this, LocationActivity::class.java).putExtra(LocationActivity.EXTRA_REASON, reason))
    }

    private fun updateLocMode() {
        if (!Store.autoLocation(this)) {
            tvLocMode.visibility = View.GONE
            return
        }
        tvLocMode.visibility = View.VISIBLE
        tvLocMode.text = when {
            !LocationHelper.hasPermission(this) -> getString(R.string.locmode_need_perm)
            checkingLocation -> getString(R.string.locmode_checking)
            locationNote != null -> getString(R.string.locmode_note, locationNote)
            Store.isComputed(this) -> getString(R.string.locmode_computed)
            else -> getString(R.string.locmode_on)
        }
    }

    // ---------------- Dil ----------------

    /**
     * Dil düğmesine dokununca açılan küçük liste: "TR · Türkçe", "FR · Français" …
     * Seçili dilin yanında işaret durur. Başka bir dil seçilince ayar kaydedilir ve ekran
     * yeni dille baştan kurulur (recreate). Widget ve bildirim kanallarının adları da yenilenir.
     */
    private fun showLanguageMenu(anchor: View) {
        val current = Lang.code(this)
        val menu = PopupMenu(this, anchor)
        Lang.CODES.forEachIndexed { i, code ->
            val title = code.uppercase(java.util.Locale.ROOT) + " · " + Lang.NATIVE_NAMES[code]
            menu.menu.add(0, i, i, title).isChecked = code == current
        }
        // Tek seçimli liste: seçili dilin yanında yuvarlak işaret görünür.
        menu.menu.setGroupCheckable(0, true, true)
        menu.setOnMenuItemClickListener { item ->
            val code = Lang.CODES[item.itemId]
            if (code != current) {
                Lang.set(this, code)
                Notifications.createChannel(applicationContext)
                PrayerWidget.updateAll(applicationContext)
                recreate()
            }
            true
        }
        menu.show()
    }

    /**
     * Hicri tarih satırı. Türkçede Diyanet'in yazdığı hâli aynen gösterilir.
     * Öteki dillerde aynı gün (Diyanet'e göre hizalanmış takvim, bkz. Hijri) ay adları
     * o dilde yazılarak gösterilir: "5 Jumada al-Ula 1448 AH".
     */
    private fun hijriText(day: PrayerDay?): String {
        if (Lang.code(this) == "tr") return day?.hijri ?: ""
        if (day == null) return ""
        val h = Hijri.of(day.date)
        val month = resources.getStringArray(R.array.hijri_months)[h.month - 1]
        return getString(R.string.hijri_date, h.day, month, h.year)
    }

    // ---------------- Ekran ----------------

    /** Ekrandaki tarih, yer ve 6 vakti yeniden doldurur. */
    private fun render() {
        val days = Times.days(this)
        val today = LocalDate.now()
        val day = Prayers.day(days, today)
        shownDate = today

        tvPlace.text = getString(R.string.place_label, Store.placeName(this))
        tvDate.text = today.format(dateFormat)
        tvHijri.text = hijriText(day)
        for (i in 0..5) {
            rowViews[i].findViewById<TextView>(R.id.time).text = day?.times?.get(i)?.format(HHMM) ?: "--:--"
        }
        if (day == null) tvNextLabel.text = getString(R.string.times_updating)
    }

    /** Her saniye: geri sayımı ve o anki vakit vurgusunu günceller. */
    private fun tick() {
        val now = LocalDateTime.now()
        if (now.toLocalDate() != shownDate) render()

        val days = Times.days(this)
        val next = Prayers.next(days, now)
        val current = Prayers.current(days, now)

        if (next == null) {
            tvCountdown.text = "--:--:--"
        } else {
            tvNextLabel.text = getString(R.string.next_label, Lang.prayerName(this, next.index))
            tvCountdown.text = formatDuration(Duration.between(now, next.at).seconds + 1)
            if (next.at != shownNext) {
                shownNext = next.at
                PrayerWidget.updateAll(this)
            }
        }

        // Gece yarısından sonra imsaka kadar dünün yatsısı sürer; o zaman hiçbir satır vurgulanmaz.
        val activeIndex = if (current != null && current.day == now.toLocalDate()) current.index else -1
        for (i in 0..5) {
            rowViews[i].setBackgroundResource(if (i == activeIndex) R.drawable.row_active else 0)
        }
    }

    private fun maybeRefresh() {
        if (refreshing || !Store.needsRefresh(this)) return
        refreshing = true
        tvStatus.text = getString(R.string.times_refreshing)
        val app = applicationContext
        Thread {
            val ok = Store.refresh(app)
            if (ok) {
                AlarmScheduler.schedule(app)
                PrayerWidget.updateAll(app)
            }
            runOnUiThread {
                refreshing = false
                if (!isFinishing) {
                    render()
                    updateStatus(if (ok) null else getString(R.string.offline_cached))
                }
            }
        }.start()
    }

    private fun updateStatus(message: String? = null) {
        val lines = ArrayList<String>()
        if (message != null) lines.add(message)

        var action: (() -> Unit)? = null
        if (Store.notificationsOn(this)) {
            if (!Notifications.canPost(this)) {
                lines.add(getString(R.string.notif_perm_off))
                action = {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                    )
                }
            } else if (!AlarmScheduler.canExact(this)) {
                lines.add(getString(R.string.exact_alarm_needed))
                action = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    }
                }
            }
        }
        lines.add(Times.sourceLabel(this))
        tvStatus.text = lines.joinToString("\n")
        val a = action
        if (a != null) tvStatus.setOnClickListener { a() } else tvStatus.setOnClickListener(null)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33 || Notifications.canPost(this)) return
        if (Store.askedNotificationPermission(this)) return
        Store.setAskedNotificationPermission(this)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LocationConsent.REQUEST_CODE) {
            // İzin penceresi kapandı: açıklama penceresinin bekleme işaretini kaldır,
            // yoksa resumeFlow hiç ilerlemez ve ekran boş kalır.
            dialogOpen = false
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Store.setAutoLocation(this, false)
                Toast.makeText(this, getString(R.string.loc_perm_denied), Toast.LENGTH_LONG).show()
            }
            // Sonrası onResume -> resumeFlow ile devam eder.
        }
        updateStatus()
    }
}
