package com.aydinmtl.namazvakitleri

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.time.Instant
import java.time.ZoneId

/**
 * Kullanıcının seçtiği ezan ses dosyasını çalar. Android, uzun süren ses çalmayı yalnızca
 * "ön plan servisi" olarak, görünen bir bildirimle (Durdur düğmeli) yapmaya izin verir.
 */
class AdhanService : Service() {

    companion object {
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_INDEX = "index" // hangi vakit (deneme çalışında yok)
        private const val EXTRA_AT = "at"       // vaktin zamanı (ms)
        private const val NOTIF_ID = 300
        private const val MAX_MS = 6 * 60_000L // güvenlik: en çok 6 dk

        /** Telefon sessiz/titreşimdeyse ve kullanıcı öyle istediyse çalma. */
        fun allowedNow(ctx: Context): Boolean {
            if (!Store.adhanRespectSilent(ctx)) return true
            val am = ctx.getSystemService(AudioManager::class.java) ?: return true
            return am.ringerMode == AudioManager.RINGER_MODE_NORMAL
        }

        /**
         * Vakit girince ezanı başlatır. Başlatılamazsa false döner; o zaman çağıran taraf
         * vakit bildirimini sesli gönderir.
         */
        fun start(ctx: Context, m: Moment): Boolean {
            val atMs = m.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return startIntent(
                ctx,
                Intent(ctx, AdhanService::class.java)
                    .putExtra(EXTRA_TITLE, Lang.prayerName(ctx, m.index))
                    .putExtra(EXTRA_INDEX, m.index)
                    .putExtra(EXTRA_AT, atMs),
            )
        }

        /** Ayarlar'daki "Dinle" düğmesi: bildirimle ilgisi yok, yalnızca sesi dener. */
        fun startTest(ctx: Context): Boolean =
            startIntent(ctx, Intent(ctx, AdhanService::class.java).putExtra(EXTRA_TITLE, Lang.str(ctx, R.string.adhan_test)))

        private fun startIntent(ctx: Context, i: Intent): Boolean = try {
            ctx.startForegroundService(i)
            true
        } catch (e: Exception) {
            // Android 12+ arka plandan ön plan servisi başlatmayı bazı durumlarda reddeder
            // (ForegroundServiceStartNotAllowedException). Çağıran taraf sesli bildirim gösterir.
            false
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Çalınan vakit; ezan çalamazsa bu vakit için sesli bildirim gösterilir. Denemede null. */
    private var moment: Moment? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: Lang.str(this, R.string.adhan_default)
        val index = intent?.getIntExtra(EXTRA_INDEX, -1) ?: -1
        val atMs = intent?.getLongExtra(EXTRA_AT, 0L) ?: 0L
        moment = if (index in PRAYER_NAMES.indices) {
            Moment(index, Instant.ofEpochMilli(atMs).atZone(ZoneId.systemDefault()).toLocalDateTime())
        } else {
            null
        }
        Notifications.createChannel(this)

        val stopPi = PendingIntent.getService(
            this, 4, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, Notifications.CHANNEL_ADHAN)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle(Lang.str(this, R.string.adhan_playing, title))
            .setContentText(Lang.str(this, R.string.adhan_tap_stop))
            .setContentIntent(stopPi)
            .setDeleteIntent(stopPi)
            .addAction(Notification.Action.Builder(null, Lang.str(this, R.string.adhan_stop), stopPi).build())
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            soundFallback()
            stopSelf()
            return START_NOT_STICKY
        }

        play()
        handler.postDelayed({ stopEverything() }, MAX_MS)
        return START_NOT_STICKY
    }

    private fun play() {
        val uri = Store.adhanUri(this)?.let { Uri.parse(it) }
        if (uri == null) {
            soundFallback()
            stopEverything()
            return
        }
        player?.release()
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            p.setDataSource(this, uri)
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { stopEverything() }
            // Dosya silinmiş ya da okunamıyorsa: ezan yerine sesli bildirim.
            p.setOnErrorListener { _, _, _ -> soundFallback(); stopEverything(); true }
            p.prepareAsync()
            player = p
        } catch (e: Exception) {
            soundFallback()
            stopEverything()
        }
    }

    /**
     * Ezan çalınamadı: vakit bildirimini (aynı numarayla) bu sefer SESLİ göster.
     * Böylece kullanıcı vakti kaçırmaz. Ayarlar'daki denemede bir şey yapılmaz.
     */
    private fun soundFallback() {
        val m = moment ?: return
        moment = null // aynı vakit için iki kez yapma
        Notifications.showPrayer(this, m, silent = false)
    }

    private fun stopEverything() {
        handler.removeCallbacksAndMessages(null)
        try {
            player?.stop()
        } catch (e: Exception) {
        }
        player?.release()
        player = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }
}
