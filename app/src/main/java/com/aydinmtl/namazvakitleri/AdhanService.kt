package com.aydinmtl.namazvakitleri

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

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
        private const val MAX_MS = 6 * 60_000L // güvenlik: en çok 6 dk

        /**
         * Uyanık tutma kilidi (işlemciyi uyutmaz; ekranı açmaz).
         *
         * Vakit alarmı telefonu yalnızca alarm alıcısı çalıştığı sürece uyanık tutar. Alıcı bitince,
         * bu servis açılıp ses dosyası hazırlanana kadar telefon yeniden uyuyabilir; o zaman ezan
         * ekran kapalıyken hiç başlamaz ya da gecikir. Kilit, ezan BAŞLATILIRKEN (alıcının içinde)
         * alınır ve ezan bitince bırakılır. Süresi sınırlıdır: bir hata olsa da kendiliğinden düşer.
         */
        private var wakeLock: PowerManager.WakeLock? = null

        @Synchronized
        private fun acquireWakeLock(ctx: Context) {
            if (wakeLock?.isHeld == true) return
            try {
                val pm = ctx.applicationContext.getSystemService(PowerManager::class.java) ?: return
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Kiblenuma:ezan").apply {
                    setReferenceCounted(false)
                    acquire(MAX_MS + 30_000L)
                }
            } catch (e: Exception) {
                // Kilit alınamazsa ezan yine denenir (ekran açıkken zaten sorun olmaz).
            }
        }

        @Synchronized
        private fun releaseWakeLock() {
            try {
                wakeLock?.let { if (it.isHeld) it.release() }
            } catch (e: Exception) {
            }
            wakeLock = null
        }

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
            val atMs = m.instant.toEpochMilli()
            return startIntent(
                ctx,
                Intent(ctx, AdhanService::class.java)
                    .putExtra(EXTRA_TITLE, Lang.prayerName(ctx, m.index))
                    .putExtra(EXTRA_INDEX, m.index)
                    .putExtra(EXTRA_AT, atMs)
                    .putExtra(AlarmScheduler.EXTRA_CLOCK, m.clock.toSecondOfDay())
                    .putExtra(AlarmScheduler.EXTRA_DAY, m.day.toEpochDay()),
            )
        }

        /** Ayarlar'daki "Dinle" düğmesi: bildirimle ilgisi yok, yalnızca sesi dener. */
        fun startTest(ctx: Context): Boolean =
            startIntent(ctx, Intent(ctx, AdhanService::class.java).putExtra(EXTRA_TITLE, Lang.str(ctx, R.string.adhan_test)))

        private fun startIntent(ctx: Context, i: Intent): Boolean {
            acquireWakeLock(ctx)
            return try {
                ctx.startForegroundService(i)
                true
            } catch (e: Exception) {
                // Android 12+ arka plandan ön plan servisi başlatmayı bazı durumlarda reddeder
                // (ForegroundServiceStartNotAllowedException). Çağıran taraf sesli bildirim gösterir.
                releaseWakeLock()
                false
            }
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    /** Çalınan vakit; ezan çalamazsa bu vakit için sesli bildirim gösterilir. Denemede null. */
    private var moment: Moment? = null

    // ---- Ses odağı ----
    // Ezan çalarken öteki uygulamaların sesi (müzik, video) durur. Ezan sürerken telefon çalarsa
    // ya da başka bir uygulama sesi alırsa ezan kesilir; arama bitince kaldığı yerden sürdürülmez.
    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopEverything()
    }

    /** Ses odağını ister. Verilmezse (ör. telefon görüşmesi sürüyor) false: ezan çalınmaz. */
    private fun requestFocus(attrs: AudioAttributes): Boolean {
        val am = getSystemService(AudioManager::class.java) ?: return true
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
        focusRequest = request
        return am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        val request = focusRequest ?: return
        focusRequest = null
        try {
            getSystemService(AudioManager::class.java)?.abandonAudioFocusRequest(request)
        } catch (e: Exception) {
        }
    }

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
            Moment.fromEpochMillis(index, atMs,
                if (intent?.hasExtra(AlarmScheduler.EXTRA_CLOCK) == true) intent.getIntExtra(AlarmScheduler.EXTRA_CLOCK, -1) else null,
                if (intent?.hasExtra(AlarmScheduler.EXTRA_DAY) == true) intent.getLongExtra(AlarmScheduler.EXTRA_DAY, 0L) else null)
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
                startForeground(NotificationIds.ADHAN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NotificationIds.ADHAN, n)
            }
        } catch (e: Exception) {
            soundFallback()
            releaseWakeLock()
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
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        if (!requestFocus(attrs)) {
            // Ses odağı verilmedi (ör. telefon görüşmesi sürüyor): ezan yerine vakit bildirimi.
            soundFallback()
            stopEverything()
            return
        }
        try {
            val p = MediaPlayer()
            p.setAudioAttributes(attrs)
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
        abandonFocus()
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        abandonFocus()
        releaseWakeLock()
        super.onDestroy()
    }
}
