package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvPlace: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvHijri: TextView
    private lateinit var tvNextLabel: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvStatus: TextView
    private lateinit var swNotify: Switch
    private val rowViews = ArrayList<View>()

    private var shownNext: LocalDateTime? = null
    private var shownDate: LocalDate? = null
    private var refreshing = false

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
        tvDate = findViewById(R.id.tvDate)
        tvHijri = findViewById(R.id.tvHijri)
        tvNextLabel = findViewById(R.id.tvNextLabel)
        tvCountdown = findViewById(R.id.tvCountdown)
        tvStatus = findViewById(R.id.tvStatus)
        swNotify = findViewById(R.id.swNotify)

        val rows = findViewById<LinearLayout>(R.id.rows)
        for (name in PRAYER_NAMES) {
            val row = layoutInflater.inflate(R.layout.item_prayer, rows, false)
            row.findViewById<TextView>(R.id.name).text = name
            rows.addView(row)
            rowViews.add(row)
        }

        tvPlace.setOnClickListener { startActivity(Intent(this, LocationActivity::class.java)) }
        findViewById<Button>(R.id.btnImsakiye).setOnClickListener {
            startActivity(Intent(this, ImsakiyeActivity::class.java))
        }
        findViewById<Button>(R.id.btnQibla).setOnClickListener {
            startActivity(Intent(this, QiblaActivity::class.java))
        }

        swNotify.isChecked = Store.notificationsOn(this)
        swNotify.setOnCheckedChangeListener { _, on ->
            Store.setNotificationsOn(this, on)
            if (on) askNotificationPermission(force = true)
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        if (Store.districtId(this) == null) {
            startActivity(Intent(this, LocationActivity::class.java))
            return
        }
        render()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        AlarmScheduler.schedule(this)
        PrayerWidget.updateAll(this)
        maybeRefresh()
        if (Store.notificationsOn(this)) askNotificationPermission(force = false)
        updateStatus()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    /** Ekrandaki tarih, yer ve 6 vakti yeniden doldurur. */
    private fun render() {
        val days = Store.days(this)
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

        val days = Store.days(this)
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

        // İçinde bulunduğumuz vakti vurgula (yalnızca bugünün satırları için).
        // Gece yarısından sonra imsaka kadar dünün yatsısı sürer; o zaman hiçbir satır vurgulanmaz.
        val activeIndex = if (current != null && current.at.toLocalDate() == now.toLocalDate()) current.index else -1
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
        val days = Store.days(this)
        days.lastOrNull()?.let { lines.add("Kayıtlı vakitler: ${it.date.format(DateTimeFormatter.ofPattern("d MMMM", TR))} tarihine kadar.") }

        var action: (() -> Unit)? = null
        if (Store.notificationsOn(this)) {
            if (!Notifications.canPost(this)) {
                lines.add("⚠ Bildirim izni kapalı. Açmak için dokun.")
                action = { openAppNotificationSettings() }
            } else if (!AlarmScheduler.canExact(this)) {
                lines.add("⚠ Bildirimlerin tam vaktinde gelmesi için \"Alarmlar ve hatırlatıcılar\" iznini ver. Dokun.")
                action = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                    }
                }
            }
        }
        lines.add("Kaynak: Diyanet İşleri Başkanlığı vakitleri")
        tvStatus.text = lines.joinToString("\n")
        val a = action
        if (a != null) tvStatus.setOnClickListener { a() } else tvStatus.setOnClickListener(null)
    }

    private fun askNotificationPermission(force: Boolean) {
        if (Build.VERSION.SDK_INT < 33 || Notifications.canPost(this)) return
        if (!force && Store.askedNotificationPermission(this)) return
        Store.setAskedNotificationPermission(this)
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    private fun openAppNotificationSettings() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(i)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatus()
    }
}
