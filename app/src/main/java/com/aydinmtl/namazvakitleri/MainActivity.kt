package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.AlertDialog
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class MainActivity : LangActivity() {

    private companion object {
        /** Otomatik konum denetiminde telefondan yeni konum için en çok bekleme. */
        const val LOCATION_WAIT_MS = 20_000L

        /** Konum alınamadıysa ikinci denemeye kadar beklenen süre. */
        const val LOCATION_RETRY_MS = 20_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvPlace: TextView
    private lateinit var tvLocMode: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvHijri: TextView
    private lateinit var tvNextLabel: TextView
    private lateinit var tvCountdown: TextView
    private lateinit var tvStatus: TextView
    private val rowViews = ArrayList<View>()

    private var shownNext: Instant? = null
    private var shownDate: LocalDate? = null
    private var refreshing = false
    private var checkingLocation = false
    private var locationNote: String? = null
    private var dialogOpen = false

    /** Ekran önde mi? Saniyelik sayaç yalnızca ekran öndeyken çalışır. */
    private var foreground = false

    /**
     * Konum alınamadıysa ekran öndeyken bir kez daha denenir: telefonun konum servisi ilk istekte
     * geç uyanabilir (ör. uygulama yeni kurulduğunda), ikinci istek çoğu zaman hemen sonuç verir.
     */
    private var locationRetried = false
    private val retryLocation = Runnable {
        if (foreground && Store.autoLocation(this) && LocationHelper.hasPermission(this)) checkLocation()
    }

    // Tarih seçili dilde ve o dilin kuralıyla yazılır: "13 Kasım 2026, Cuma" / "vendredi 13 novembre 2026"
    private val dateFormat by lazy { Lang.dateFormat(this, R.string.date_full) }

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
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        locationRetried = false
        resumeFlow()
    }

    override fun onPause() {
        super.onPause()
        foreground = false
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(retryLocation)
    }

    /** Açılışta ne yapılacağına karar verir: izin açıklaması, konum kontrolü, vakitler. */
    private fun resumeFlow() {
        if (dialogOpen) return

        // 0) Yeni kurulumda önce dil sorulur; yoksa sıradaki konum penceresi anlaşılmayan bir dilde
        //    çıkabilir. Uygulamayı zaten kullananlara sorulmaz: dilleri olduğu gibi (Türkçe) kalır.
        if (!Store.languageChosen(this)) {
            if (Store.isExistingInstall(this)) {
                Lang.set(this, Lang.code(this))
            } else {
                dialogOpen = true
                askLanguage()
                return
            }
        }

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
        // Konum sonucu ekran arkadayken de gelebilir. O zaman sayaç başlatılmaz (boşuna her saniye
        // çalışmasın); ekran öne gelince onResume bu fonksiyonu yeniden çağırır.
        if (foreground) handler.post(ticker)
        AlarmScheduler.schedule(this)
        PrayerWidget.updateAll(this)
        maybeRefresh()
        // Yer adı seçili dilde henüz bilinmiyorsa arka planda bulunur ("Cenevre" → "Genève").
        PlaceLabels.ensure(this) {
            if (isFinishing || isDestroyed) return@ensure
            tvPlace.text = getString(R.string.place_label, Store.placeLabel(this))
            PrayerWidget.updateAll(this)
        }
    }

    // ---------------- Otomatik konum ----------------

    private fun checkLocation() {
        if (checkingLocation) return
        if (!LocationHelper.isLocationOn(this)) {
            locationNote = getString(R.string.loc_service_off_note)
            noPlaceFallback(getString(R.string.loc_service_off_reason))
            return
        }
        val request = Store.automaticPlaceRequest(this) ?: return
        checkingLocation = true
        updateLocMode()
        // 3 dakikadan yeni kayıtlı konum varsa hemen kullanılır; yoksa telefondan yenisi istenir
        // (yolda giderken eski kayıt eski şehri gösterebilir). Vakitler bu sırada ekranda olduğu
        // için beklemek bir şey engellemez: 20 sn beklenir. Yeni konum gelmezse en çok 10 dakikalık
        // kayıtlı konuma dönülür (bkz. LocationHelper.current).
        LocationHelper.current(this, maxAgeMs = 3 * 60_000L, timeoutMs = LOCATION_WAIT_MS) { loc ->
            if (isFinishing || isDestroyed) return@current
            if (!Store.isPlaceRequestCurrent(request)) {
                checkingLocation = false
                updateLocMode()
                return@current
            }
            if (loc == null) {
                checkingLocation = false
                locationNote = getString(R.string.loc_unavailable_note)
                updateLocMode()
                noPlaceFallback(getString(R.string.loc_no_report_reason))
                // Yer zaten seçiliyse biraz sonra bir kez daha dene; başarılı olursa uyarı kalkar.
                if (!locationRetried && Store.districtId(this) != null) {
                    locationRetried = true
                    handler.postDelayed(retryLocation, LOCATION_RETRY_MS)
                }
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
                    AutoLocation.resolve(app, loc, request) { msg ->
                        // İlk kurulumda hangi adımda olunduğunu göster
                        runOnUiThread { if (Store.districtId(app) == null) tvNextLabel.text = msg }
                    }
                } catch (e: Exception) {
                    if (Store.isPlaceRequestCurrent(request)) AutoLocation.Result.Failed(Lang.str(app, R.string.no_internet))
                    else AutoLocation.Result.Discarded
                }
                runOnUiThread { onLocationResult(result, loc) }
            }.start()
        }
    }

    private fun onLocationResult(r: AutoLocation.Result, loc: android.location.Location) {
        checkingLocation = false
        if (isFinishing || isDestroyed) return
        when (r) {
            is AutoLocation.Result.Discarded -> {
                // Bu iş sürerken yer başka bir yoldan değişti (elle seçim ya da başka bir konum işi).
                // Kayıtlı olan en yeni yerdir; hata değildir. Ekranı ona göre tazele.
                if (Store.districtId(this) != null) {
                    shownNext = null
                    showTimes()
                    updateStatus()
                }
            }
            is AutoLocation.Result.Changed -> {
                locationNote = null
                AutoLocation.lastFailAt = 0L
                Toast.makeText(this, getString(R.string.loc_updated, Store.placeLabel(this)), Toast.LENGTH_LONG).show()
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
                    AlarmScheduler.schedule(this)
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
     * İlk açılışta dil seçimi. Telefonun dili (destekleniyorsa) önceden seçili gelir. Başlık dört
     * dilde yazılıdır ve her dil kendi adıyla listelenir; böylece hangi dilde açılırsa açılsın anlaşılır.
     */
    private fun askLanguage() {
        val current = Lang.code(this)
        var chosen = current
        val names = Lang.CODES.map { Lang.NATIVE_NAMES[it] ?: it }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.lang_pick_title)
            .setSingleChoiceItems(names, Lang.CODES.indexOf(current)) { _, which -> chosen = Lang.CODES[which] }
            .setCancelable(false)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Lang.set(this, chosen)
                dialogOpen = false
                // Dil değiştiyse ekran yeni dille baştan kurulur; akış oradan sürer.
                if (chosen != current) recreate() else resumeFlow()
            }
            .show()
    }

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
        return Lang.hijriDate(this, Hijri.of(day.date))
    }

    // ---------------- Ekran ----------------

    /** Ekrandaki tarih, yer ve 6 vakti yeniden doldurur. */
    private fun render() {
        val days = Times.days(this)
        val today = LocalDate.now()
        val day = Prayers.day(days, today)
        shownDate = today

        tvPlace.text = getString(R.string.place_label, Store.placeLabel(this))
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
        val nowInstant = Instant.now()
        val next = Prayers.next(days, nowInstant)
        val current = Prayers.current(days, nowInstant)

        if (next == null) {
            tvCountdown.text = "--:--:--"
        } else {
            tvNextLabel.text = getString(R.string.next_label, Lang.prayerName(this, next.index))
            tvCountdown.text = formatDuration(Duration.between(nowInstant, next.instant).seconds + 1)
            if (next.instant != shownNext) {
                shownNext = next.instant
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
                    Ui.openSystemScreen(
                        this,
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
                    )
                }
            } else if (!AlarmScheduler.canExact(this)) {
                lines.add(getString(R.string.exact_alarm_needed))
                action = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        Ui.openSystemScreen(this, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
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
