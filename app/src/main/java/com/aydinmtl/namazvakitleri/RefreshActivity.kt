package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Widget'taki ↻ düğmesi bu küçük pencereyi açar: konumu bulur (otomatik konum açıksa)
 * ve vakitleri Diyanet'ten yeniler. Sonucu gösterip kendiliğinden kapanır.
 *
 * Neden ayrı bir pencere? Android, arka plandaki bir uygulamanın (widget tıklaması dahil)
 * konuma erişmesine "her zaman konum" izni olmadan izin vermez. Ekranda görünen bir pencere
 * açıkken uygulama ön planda sayılır ve normal konum izni yeterli olur.
 */
class RefreshActivity : Activity() {

    private lateinit var tvStatus: TextView
    private lateinit var progress: ProgressBar
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        if (canLocate) {
            tvStatus.text = "Konum bulunuyor…"
            LocationHelper.current(this, maxAgeMs = 2 * 60_000L) { loc ->
                work(loc, if (loc == null) "Konum alınamadı; son yer kullanıldı." else null)
            }
        } else {
            val note = when {
                !autoOn -> "Otomatik konum kapalı (Ayarlar'dan açabilirsin)."
                !LocationHelper.hasPermission(this) -> "Konum izni yok; son yer kullanıldı."
                else -> "Konum servisi kapalı; son yer kullanıldı."
            }
            work(null, note)
        }
    }

    private fun work(loc: Location?, initialNote: String?) {
        if (!isDestroyed) tvStatus.text = "Vakitler güncelleniyor…"
        val app = applicationContext
        Thread {
            var note = initialNote
            var newPlace: String? = null

            if (loc != null) {
                val r = try {
                    AutoLocation.resolve(app, loc)
                } catch (e: Exception) {
                    AutoLocation.Result.Failed("İnternete bağlanılamadı.")
                }
                when (r) {
                    is AutoLocation.Result.Changed -> newPlace = r.label
                    is AutoLocation.Result.Same -> {}
                    is AutoLocation.Result.Failed -> note = r.reason + " Son yer kullanıldı."
                }
            }

            // Yer değiştiyse vakitler zaten yeni indirildi; değilse şimdi yenile.
            val ok = newPlace != null || Store.refresh(app)
            AlarmScheduler.schedule(app)
            PrayerWidget.updateAll(app)

            val text = buildString {
                append(if (ok) "✓ Vakitler güncellendi" else "Vakitler güncellenemedi. İnternet bağlantını kontrol et.")
                append("\n📍 ").append(Store.placeName(app))
                if (newPlace != null) append(" (yeni konum)")
                if (note != null) append("\n").append(note)
            }
            runOnUiThread { showResult(text) }
        }.start()
    }

    private fun showResult(text: String) {
        if (isDestroyed || isFinishing) return
        progress.visibility = View.GONE
        tvStatus.text = text
        handler.postDelayed({ if (!isFinishing) finish() }, 2500L)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
