package com.aydinmtl.namazvakitleri

import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Widget'taki ↻ düğmesi bu küçük pencereyi açar: konumu bulur (otomatik konum açıksa)
 * ve vakitleri Diyanet'ten yeniler. Her adımı ekrana yazar, sonucu gösterip kendiliğinden kapanır.
 *
 * Neden ayrı bir pencere? Android, arka plandaki bir uygulamanın (widget tıklaması dahil)
 * konuma erişmesine "her zaman konum" izni olmadan izin vermez. Ekranda görünen bir pencere
 * açıkken uygulama ön planda sayılır ve normal konum izni yeterli olur.
 */
class RefreshActivity : LangActivity() {

    companion object {
        private const val LOCATION_TIMEOUT_MS = 8_000L   // konum için en çok bekleme
        private const val TOTAL_TIMEOUT_MS = 75_000L     // tüm iş için güvenlik sınırı
        private const val CLOSE_AFTER_MS = 3_000L        // sonucu bu kadar göster
    }

    private lateinit var tvStatus: TextView
    private lateinit var progress: ProgressBar
    private val handler = Handler(Looper.getMainLooper())

    @Volatile private var finished = false
    private var step = ""
    private var stepStartedAt = 0L

    /** Her saniye adımın yanında geçen süreyi gösterir: "Konum bulunuyor… 3 sn" */
    private val secondsTicker = object : Runnable {
        override fun run() {
            if (finished) return
            val sec = (System.currentTimeMillis() - stepStartedAt) / 1000
            tvStatus.text = if (sec >= 1) getString(R.string.refresh_seconds, step, sec.toInt()) else step
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kilit ekranındaki widget'tan basılınca telefonu açmadan görünsün.
        if (Build.VERSION.SDK_INT >= 27) setShowWhenLocked(true)
        setContentView(R.layout.dialog_refresh)
        tvStatus = findViewById(R.id.tvStatus)
        progress = findViewById(R.id.progress)

        val autoOn = Store.autoLocation(this)
        val canLocate = autoOn && LocationHelper.hasPermission(this) && LocationHelper.isLocationOn(this)

        // Hiç yer seçilmemiş ve konum da kullanılamıyorsa: uygulamayı aç.
        if (Store.districtId(this) == null && !canLocate) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        // Ne olursa olsun bu sürede bitmezse kullanıcıyı bekletme.
        handler.postDelayed({
            done(getString(R.string.refresh_timeout), ok = false)
        }, TOTAL_TIMEOUT_MS)

        if (canLocate) {
            setStep(getString(R.string.refresh_finding_location))
            LocationHelper.current(this, maxAgeMs = 2 * 60_000L, timeoutMs = LOCATION_TIMEOUT_MS) { loc ->
                work(loc, if (loc == null) getString(R.string.refresh_loc_failed) else null)
            }
        } else {
            val note = when {
                !autoOn -> getString(R.string.refresh_auto_off)
                !LocationHelper.hasPermission(this) -> getString(R.string.refresh_no_perm)
                else -> getString(R.string.refresh_service_off)
            }
            work(null, note)
        }
    }

    private fun setStep(text: String) {
        if (finished) return
        step = text
        stepStartedAt = System.currentTimeMillis()
        tvStatus.text = text
        handler.removeCallbacks(secondsTicker)
        handler.postDelayed(secondsTicker, 1000)
    }

    private fun work(loc: Location?, initialNote: String?) {
        if (finished) return
        val app = applicationContext
        Thread {
            var note = initialNote
            var newPlace: String? = null

            if (loc != null) {
                val r = try {
                    AutoLocation.resolve(app, loc) { msg -> runOnUiThread { setStep(msg) } }
                } catch (e: Exception) {
                    AutoLocation.Result.Failed(getString(R.string.no_internet))
                }
                when (r) {
                    is AutoLocation.Result.Changed -> newPlace = r.label
                    is AutoLocation.Result.Same -> {}
                    is AutoLocation.Result.Failed -> note = getString(R.string.refresh_failed_last, r.reason)
                }
            }

            // Yer değiştiyse vakitler zaten yeni indirildi; değilse şimdi yenile.
            var ok = newPlace != null
            if (!ok) {
                runOnUiThread { setStep(getString(R.string.refresh_downloading)) }
                ok = Store.refresh(app)
            }
            AlarmScheduler.schedule(app)
            PrayerWidget.updateAll(app)

            val text = buildString {
                append(getString(if (ok) R.string.refresh_ok else R.string.refresh_fail))
                append("\n📍 ").append(Store.placeName(app))
                if (newPlace != null) {
                    append(getString(R.string.refresh_new_place))
                } else if (loc != null && note == null) {
                    append(getString(R.string.refresh_same_place))
                }
                if (note != null) append("\n").append(note)
            }
            runOnUiThread { done(text, ok) }
        }.start()
    }

    /** Sonucu gösterir ve pencereyi kapatır. Pencere kapatılmışsa sonucu kısa bildirimle (Toast) söyler. */
    private fun done(text: String, ok: Boolean) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        if (isDestroyed || isFinishing) {
            Toast.makeText(applicationContext, text, Toast.LENGTH_LONG).show()
            return
        }
        progress.visibility = View.GONE
        tvStatus.text = text
        tvStatus.setTextColor(getColor(if (ok) R.color.text else R.color.error))
        handler.postDelayed({ finish() }, CLOSE_AFTER_MS)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Kullanıcı pencereyi erken kapattıysa iş arka planda sürer; sonuç Toast ile gösterilir.
        if (!finished) handler.removeCallbacks(secondsTicker)
    }
}
