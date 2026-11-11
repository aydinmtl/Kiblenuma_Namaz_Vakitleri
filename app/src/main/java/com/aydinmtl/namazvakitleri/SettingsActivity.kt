package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.format.DateTimeFormatter

class SettingsActivity : Activity() {

    private lateinit var tvCurrentPlace: TextView
    private lateinit var swAuto: Switch
    private lateinit var swNotify: Switch
    private lateinit var tvPermissions: TextView
    private lateinit var tvData: TextView
    private lateinit var btnRefresh: Button
    private val checks = ArrayList<CheckBox>()

    /** Kod içinden switch değiştirirken dinleyicinin tetiklenmemesi için. */
    private var binding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        tvCurrentPlace = findViewById(R.id.tvCurrentPlace)
        swAuto = findViewById(R.id.swAuto)
        swNotify = findViewById(R.id.swNotify)
        tvPermissions = findViewById(R.id.tvPermissions)
        tvData = findViewById(R.id.tvData)
        btnRefresh = findViewById(R.id.btnRefresh)

        // ---- Konum ----
        swAuto.setOnCheckedChangeListener { _, on ->
            if (binding) return@setOnCheckedChangeListener
            if (!on) {
                Store.setAutoLocation(this, false)
            } else if (LocationHelper.hasPermission(this)) {
                Store.setAutoLocation(this, true)
                Toast.makeText(this, "Ana ekrana dönünce konumun kontrol edilecek.", Toast.LENGTH_SHORT).show()
            } else {
                // İzin yok: önce nedenini anlat, sonra iste.
                LocationConsent.show(this, onReady = { bind() }, onManual = { bind() })
            }
        }
        findViewById<Button>(R.id.btnManual).setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }

        // ---- Bildirimler ----
        val box = findViewById<LinearLayout>(R.id.prayerChecks)
        for (i in PRAYER_NAMES.indices) {
            val cb = CheckBox(this)
            cb.text = if (i == SUNRISE) "${PRAYER_NAMES[i]} (namaz vakti değil)" else PRAYER_NAMES[i]
            cb.textSize = 16f
            cb.setTextColor(getColor(R.color.text))
            cb.setOnCheckedChangeListener { _, on ->
                if (!binding) Store.setNotifyFor(this, i, on)
            }
            box.addView(cb)
            checks.add(cb)
        }
        swNotify.setOnCheckedChangeListener { _, on ->
            if (binding) return@setOnCheckedChangeListener
            Store.setNotificationsOn(this, on)
            if (on && Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this)) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            bind()
        }
        findViewById<Button>(R.id.btnSound).setOnClickListener {
            Notifications.createChannel(this)
            val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL)
            startActivity(i)
        }
        tvPermissions.setOnClickListener { openPermissionFix() }

        // ---- Vakitler ----
        btnRefresh.setOnClickListener { refreshNow() }

        // ---- Hakkında ----
        @Suppress("DEPRECATION")
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        findViewById<TextView>(R.id.tvAbout).text =
            "Namaz Vakitleri $version\n" +
            "Reklamsız ve ücretsiz. Kişisel veri toplamaz.\n" +
            "Vakitler: Diyanet İşleri Başkanlığı (ezanvakti.emushaf.net üzerinden)."
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    /** Ekrandaki tüm değerleri kayıtlı ayarlardan doldurur. */
    private fun bind() {
        binding = true
        tvCurrentPlace.text = "📍 " + Store.placeName(this).ifEmpty { "Yer seçilmedi" }
        swAuto.isChecked = Store.autoLocation(this) && LocationHelper.hasPermission(this)

        val notifyOn = Store.notificationsOn(this)
        swNotify.isChecked = notifyOn
        for (i in checks.indices) {
            checks[i].isChecked = Store.notifyFor(this, i)
            checks[i].isEnabled = notifyOn
        }

        val warnings = ArrayList<String>()
        if (notifyOn && !Notifications.canPost(this)) warnings.add("⚠ Bildirim izni kapalı. Açmak için dokun.")
        else if (notifyOn && !AlarmScheduler.canExact(this)) {
            warnings.add("⚠ Tam vaktinde bildirim için \"Alarmlar ve hatırlatıcılar\" iznini ver. Dokun.")
        }
        tvPermissions.text = warnings.joinToString("\n")
        tvPermissions.visibility = if (warnings.isEmpty()) View.GONE else View.VISIBLE

        val last = Store.lastDay(this)
        tvData.text = if (last == null) "Kayıtlı vakit yok."
        else "Kayıtlı vakitler: ${last.format(DateTimeFormatter.ofPattern("d MMMM yyyy", TR))} tarihine kadar. " +
            "Süre azalınca kendiliğinden yenilenir."
        binding = false
    }

    private fun openPermissionFix() {
        if (!Notifications.canPost(this)) {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        } else if (Build.VERSION.SDK_INT >= 31 && !AlarmScheduler.canExact(this)) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    private fun refreshNow() {
        if (Store.districtId(this) == null) return
        btnRefresh.isEnabled = false
        btnRefresh.text = "Güncelleniyor…"
        val app = applicationContext
        Thread {
            val ok = Store.refresh(app)
            if (ok) {
                AlarmScheduler.schedule(app)
                PrayerWidget.updateAll(app)
            }
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                btnRefresh.isEnabled = true
                btnRefresh.text = "Vakitleri şimdi güncelle"
                Toast.makeText(
                    this,
                    if (ok) "Vakitler güncellendi." else "Güncellenemedi. İnternet bağlantını kontrol et.",
                    Toast.LENGTH_SHORT,
                ).show()
                bind()
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LocationConsent.REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Store.setAutoLocation(this, granted)
            if (granted) Toast.makeText(this, "Ana ekrana dönünce konumun kontrol edilecek.", Toast.LENGTH_SHORT).show()
            else Toast.makeText(this, "Konum izni verilmedi.", Toast.LENGTH_SHORT).show()
        }
        bind()
    }
}
