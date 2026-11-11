package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.Activity
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
import android.widget.TextView
import android.widget.Toast
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

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

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", TR)

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
        for (name in PRAYER_NAMES) {
            val row = layoutInflater.inflate(R.layout.item_prayer, rows, false)
            row.findViewById<TextView>(R.id.name).text = name
            rows.addView(row)
            rowViews.add(row)
        }

        tvPlace.setOnClickListener { startActivity(Intent(this, LocationActivity::class.java)) }
        tvLocMode.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
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
        findViewById<Button>(R.id.btnDaily).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java).putExtra(QuizActivity.EXTRA_DAILY, true))
        }
    }

    /** "Günün sorusu" düğmesi: bugün cevaplandıysa ✓ işareti. */
    private fun updateDailyButton() {
        val done = Store.dailyDoneDate(this) == LocalDate.now().toString()
        findViewById<Button>(R.id.btnDaily).text = if (done) "💡  Günün sorusu ✓" else "💡  Günün sorusu"
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
            tvPlace.text = "📍 Konum aranıyor…"
            tvDate.text = LocalDate.now().format(dateFormat)
            tvNextLabel.text = "Bulunduğun yer bulunuyor…"
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
            locationNote = "Telefonun konum servisi kapalı; son seçilen yer kullanılıyor."
            noPlaceFallback("telefonun konum servisi kapalı.")
            return
        }
        checkingLocation = true
        updateLocMode()
        LocationHelper.current(this, timeoutMs = 10_000L) { loc ->
            if (isFinishing || isDestroyed) return@current
            if (loc == null) {
                checkingLocation = false
                locationNote = "Konum alınamadı; son seçilen yer kullanılıyor."
                updateLocMode()
                noPlaceFallback("telefon konumu bildirmedi (10 sn içinde).")
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
                    AutoLocation.Result.Failed("İnternete bağlanılamadı.")
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
                Toast.makeText(this, "Konum güncellendi: ${r.label}", Toast.LENGTH_LONG).show()
                shownNext = null
                showTimes()
                // İlk kurulumda yer ancak şimdi bulundu: bildirim iznini şimdi sor.
                if (Store.notificationsOn(this)) askNotificationPermission()
                updateStatus()
            }
            is AutoLocation.Result.Same -> locationNote = null
            is AutoLocation.Result.Failed -> {
                locationNote = r.reason + " Son seçilen yer kullanılıyor."
                // Aynı yerde her açılışta yeniden denemesin; 15 km yer değiştirince ya da ↻ ile tekrar dener.
                Store.saveAutoPoint(this, loc.latitude, loc.longitude)
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
            !LocationHelper.hasPermission(this) -> "⚠ Otomatik konum için izin gerekli. Ayarlar'a gitmek için dokun."
            checkingLocation -> "📡 Otomatik konum · kontrol ediliyor…"
            locationNote != null -> "📡 Otomatik konum · $locationNote"
            else -> "📡 Otomatik konum açık"
        }
    }

    // ---------------- Ekran ----------------

    /** Ekrandaki tarih, yer ve 6 vakti yeniden doldurur. */
    private fun render() {
        val days = Times.days(this)
        val today = LocalDate.now()
        val day = Prayers.day(days, today)
        shownDate = today

        tvPlace.text = "📍 ${Store.placeName(this)}  ›"
        tvDate.text = today.format(dateFormat)
        tvHijri.text = day?.hijri ?: ""
        for (i in 0..5) {
            rowViews[i].findViewById<TextView>(R.id.time).text = day?.times?.get(i)?.format(HHMM) ?: "--:--"
        }
        if (day == null) tvNextLabel.text = "Vakitler güncelleniyor…"
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
            tvNextLabel.text = "${next.name} vaktine kalan"
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
        tvStatus.text = "Vakitler Diyanet'ten güncelleniyor…"
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
                    updateStatus(if (ok) null else "İnternete bağlanılamadı; kayıtlı vakitler gösteriliyor.")
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
                lines.add("⚠ Bildirim izni kapalı. Açmak için dokun.")
                action = {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                    )
                }
            } else if (!AlarmScheduler.canExact(this)) {
                lines.add("⚠ Bildirimlerin tam vaktinde gelmesi için \"Alarmlar ve hatırlatıcılar\" iznini ver. Dokun.")
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
                Toast.makeText(this, "Konum izni verilmedi. Şehrini elle seçebilirsin.", Toast.LENGTH_LONG).show()
            }
            // Sonrası onResume -> resumeFlow ile devam eder.
        }
        updateStatus()
    }
}
