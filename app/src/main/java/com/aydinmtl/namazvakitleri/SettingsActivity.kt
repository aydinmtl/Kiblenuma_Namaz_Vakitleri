package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.time.format.DateTimeFormatter

class SettingsActivity : Activity() {

    companion object {
        private const val REQ_PICK_ADHAN = 21
        private val REMINDER_OPTIONS = intArrayOf(0, 5, 10, 15, 20, 30, 45, 60)
    }

    private lateinit var tvCurrentPlace: TextView
    private lateinit var swAuto: Switch
    private lateinit var rowMethod: TextView
    private lateinit var tvMethodInfo: TextView
    private lateinit var rowAsr: TextView
    private lateinit var swNotify: Switch
    private lateinit var rowReminder: TextView
    private lateinit var swAdhan: Switch
    private lateinit var tvAdhanFile: TextView
    private lateinit var cbSilent: CheckBox
    private lateinit var tvPermissions: TextView
    private lateinit var tvData: TextView
    private lateinit var btnRefresh: Button
    private val checks = ArrayList<CheckBox>()
    private var adhanTesting = false

    /** Kod içinden switch değiştirirken dinleyicinin tetiklenmemesi için. */
    private var binding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        tvCurrentPlace = findViewById(R.id.tvCurrentPlace)
        swAuto = findViewById(R.id.swAuto)
        rowMethod = findViewById(R.id.rowMethod)
        tvMethodInfo = findViewById(R.id.tvMethodInfo)
        rowAsr = findViewById(R.id.rowAsr)
        swNotify = findViewById(R.id.swNotify)
        rowReminder = findViewById(R.id.rowReminder)
        swAdhan = findViewById(R.id.swAdhan)
        tvAdhanFile = findViewById(R.id.tvAdhanFile)
        cbSilent = findViewById(R.id.cbSilent)
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

        // ---- Vakit hesaplama ----
        rowMethod.setOnClickListener { chooseMethod() }
        rowAsr.setOnClickListener { chooseAsr() }

        // ---- Bildirimler ----
        val box = findViewById<LinearLayout>(R.id.prayerChecks)
        for (i in PRAYER_NAMES.indices) {
            val cb = CheckBox(this)
            cb.text = if (i == SUNRISE) "${PRAYER_NAMES[i]} (namaz vakti değil)" else PRAYER_NAMES[i]
            cb.textSize = 16f
            cb.setTextColor(getColor(R.color.text))
            cb.setOnCheckedChangeListener { _, on ->
                if (!binding) {
                    Store.setNotifyFor(this, i, on)
                    AlarmScheduler.schedule(this)
                }
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
            AlarmScheduler.schedule(this)
            bind()
        }
        rowReminder.setOnClickListener { chooseReminder() }
        findViewById<Button>(R.id.btnSound).setOnClickListener {
            Notifications.createChannel(this)
            val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, Notifications.CHANNEL)
            startActivity(i)
        }
        tvPermissions.setOnClickListener { openPermissionFix() }

        // ---- Ezan sesi ----
        swAdhan.setOnCheckedChangeListener { _, on ->
            if (binding) return@setOnCheckedChangeListener
            if (on && Store.adhanUri(this) == null) {
                Toast.makeText(this, "Önce bir ezan ses dosyası seç.", Toast.LENGTH_SHORT).show()
                pickAdhan()
            }
            Store.setAdhanOn(this, on)
            bind()
        }
        findViewById<Button>(R.id.btnPickAdhan).setOnClickListener { pickAdhan() }
        findViewById<Button>(R.id.btnTestAdhan).setOnClickListener { testAdhan(it as Button) }
        cbSilent.setOnCheckedChangeListener { _, on -> if (!binding) Store.setAdhanRespectSilent(this, on) }

        // ---- Vakitler ----
        btnRefresh.setOnClickListener { refreshNow() }

        // ---- Hakkında ----
        @Suppress("DEPRECATION")
        val version = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "?" }
        findViewById<TextView>(R.id.tvAbout).text =
            "Kıblenüma $version\n" +
            "Reklamsız ve ücretsiz. Kişisel veri toplamaz.\n" +
            "Diyanet vakitleri ezanvakti.emushaf.net üzerinden alınır. Diğer yöntemler telefonda hesaplanır.\n" +
            "Camiler: © OpenStreetMap katkıcıları."
    }

    override fun onResume() {
        super.onResume()
        bind()
    }

    override fun onPause() {
        super.onPause()
        if (adhanTesting) {
            AdhanService.stop(this)
            adhanTesting = false
        }
    }

    /** "Başlık" üstte, "değer ›" altta altın renkte. */
    private fun row(title: String, value: String): CharSequence {
        val s = SpannableStringBuilder(title).append("\n")
        val start = s.length
        s.append("$value  ›")
        s.setSpan(ForegroundColorSpan(getColor(R.color.gold)), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        s.setSpan(RelativeSizeSpan(0.9f), start, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /** Ekrandaki tüm değerleri kayıtlı ayarlardan doldurur. */
    private fun bind() {
        binding = true
        tvCurrentPlace.text = "📍 " + Store.placeName(this).ifEmpty { "Yer seçilmedi" }
        swAuto.isChecked = Store.autoLocation(this) && LocationHelper.hasPermission(this)

        val m = Store.method(this)
        rowMethod.text = row("Hesaplama yöntemi", m.title)
        tvMethodInfo.text = m.details() + if (Times.isFallback(this)) "\n⚠ Bu yerin koordinatı bulunamadı; şimdilik Diyanet vakitleri gösteriliyor." else ""
        rowAsr.text = row("İkindi hesabı", if (Store.asrHanafi(this)) "Hanefi (asr-ı sani)" else "Standart (asr-ı evvel)")
        rowAsr.isEnabled = m != CalcMethod.DIYANET
        rowAsr.alpha = if (m != CalcMethod.DIYANET) 1f else 0.5f

        val notifyOn = Store.notificationsOn(this)
        swNotify.isChecked = notifyOn
        for (i in checks.indices) {
            checks[i].isChecked = Store.notifyFor(this, i)
            checks[i].isEnabled = notifyOn
        }
        val rem = Store.reminderMinutes(this)
        rowReminder.text = row("Vakitten önce hatırlat", if (rem == 0) "Kapalı" else "$rem dakika önce")

        val adhanName = Store.adhanName(this)
        swAdhan.isChecked = Store.adhanOn(this)
        tvAdhanFile.text = if (Store.adhanUri(this) == null) {
            "Uygulamada hazır ezan sesi yok. Telefonundaki bir ezan ses dosyasını (mp3 vb.) seç. " +
                "Ezan, vakit girince çalar; bildirimdeki \"Durdur\" ile susturabilirsin."
        } else {
            "Seçili dosya: $adhanName"
        }
        cbSilent.isChecked = Store.adhanRespectSilent(this)

        val warnings = ArrayList<String>()
        if (notifyOn && !Notifications.canPost(this)) warnings.add("⚠ Bildirim izni kapalı. Açmak için dokun.")
        else if (notifyOn && !AlarmScheduler.canExact(this)) {
            warnings.add("⚠ Tam vaktinde bildirim için \"Alarmlar ve hatırlatıcılar\" iznini ver. Dokun.")
        }
        tvPermissions.text = warnings.joinToString("\n")
        tvPermissions.visibility = if (warnings.isEmpty()) View.GONE else View.VISIBLE

        val last = Store.lastDay(this)
        tvData.text = if (last == null) "Kayıtlı Diyanet vakti yok."
        else "Kayıtlı Diyanet vakitleri: ${last.format(DateTimeFormatter.ofPattern("d MMMM yyyy", TR))} tarihine kadar. " +
            "Süre azalınca kendiliğinden yenilenir."
        binding = false
    }

    // ---------------- Seçim pencereleri ----------------

    private fun chooseMethod() {
        val methods = CalcMethod.values()
        val current = methods.indexOf(Store.method(this))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Hesaplama yöntemi")
            .setSingleChoiceItems(methods.map { it.title }.toTypedArray(), current) { d, which ->
                d.dismiss()
                applyMethod(methods[which])
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun applyMethod(m: CalcMethod) {
        Store.setMethod(this, m)
        val app = applicationContext
        if (m != CalcMethod.DIYANET && Store.placePoint(this) == null) {
            // Elle seçilmiş eski yerlerde koordinat yok: yer adından bul.
            Toast.makeText(this, "Yerin koordinatı bulunuyor…", Toast.LENGTH_SHORT).show()
            val name = Store.placeName(this)
            Thread {
                val p = AutoLocation.geocodeName(app, listOf(name))
                Store.savePlacePoint(app, p)
                AlarmScheduler.schedule(app)
                PrayerWidget.updateAll(app)
                runOnUiThread {
                    if (p == null) {
                        Toast.makeText(this, "Koordinat bulunamadı. Otomatik konumu açmayı dene.", Toast.LENGTH_LONG).show()
                    }
                    if (!isFinishing) bind()
                }
            }.start()
        } else {
            AlarmScheduler.schedule(app)
            PrayerWidget.updateAll(app)
        }
        bind()
    }

    private fun chooseAsr() {
        val items = arrayOf("Standart (Şafii, Maliki, Hanbeli)", "Hanefi (asr-ı sani)")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("İkindi hesabı")
            .setSingleChoiceItems(items, if (Store.asrHanafi(this)) 1 else 0) { d, which ->
                d.dismiss()
                Store.setAsrHanafi(this, which == 1)
                AlarmScheduler.schedule(this)
                PrayerWidget.updateAll(this)
                bind()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun chooseReminder() {
        val labels = REMINDER_OPTIONS.map { if (it == 0) "Kapalı" else "$it dakika önce" }.toTypedArray()
        val current = REMINDER_OPTIONS.indexOf(Store.reminderMinutes(this)).coerceAtLeast(0)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Vakitten önce hatırlat")
            .setSingleChoiceItems(labels, current) { d, which ->
                d.dismiss()
                Store.setReminderMinutes(this, REMINDER_OPTIONS[which])
                AlarmScheduler.schedule(this)
                bind()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // ---------------- Ezan sesi ----------------

    private fun pickAdhan() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("audio/*")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_PICK_ADHAN)
        } catch (e: Exception) {
            Toast.makeText(this, "Dosya seçici açılamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_PICK_ADHAN || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            // Telefon yeniden başlasa da dosyayı okuyabilmek için kalıcı izin al.
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) {
        }
        Store.setAdhan(this, uri.toString(), fileName(uri))
        Store.setAdhanOn(this, true)
        Toast.makeText(this, "Ezan sesi seçildi.", Toast.LENGTH_SHORT).show()
        bind()
    }

    private fun fileName(uri: Uri): String {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: "ses dosyası"
            }
        } catch (e: Exception) {
        }
        return "ses dosyası"
    }

    private fun testAdhan(btn: Button) {
        if (Store.adhanUri(this) == null) {
            pickAdhan()
            return
        }
        if (adhanTesting) {
            AdhanService.stop(this)
            adhanTesting = false
            btn.text = "Dinle"
        } else {
            if (AdhanService.startTest(this)) {
                adhanTesting = true
                btn.text = "Durdur"
            } else {
                Toast.makeText(this, "Ezan sesi başlatılamadı.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------- Diğer ----------------

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
