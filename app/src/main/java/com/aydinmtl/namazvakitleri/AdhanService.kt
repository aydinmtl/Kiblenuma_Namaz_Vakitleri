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

/**
 * Kullanıcının seçtiği ezan ses dosyasını çalar. Android, uzun süren ses çalmayı yalnızca
 * "ön plan servisi" olarak, görünen bir bildirimle (Durdur düğmeli) yapmaya izin verir.
 */
class AdhanService : Service() {

    companion object {
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TITLE = "title"
        private const val NOTIF_ID = 300
        private const val MAX_MS = 6 * 60_000L // güvenlik: en çok 6 dk

        /** Telefon sessiz/titreşimdeyse ve kullanıcı öyle istediyse çalma. */
        fun allowedNow(ctx: Context): Boolean {
            if (!Store.adhanRespectSilent(ctx)) return true
            val am = ctx.getSystemService(AudioManager::class.java) ?: return true
            return am.ringerMode == AudioManager.RINGER_MODE_NORMAL
        }

        fun start(ctx: Context, title: String) {
            val i = Intent(ctx, AdhanService::class.java).putExtra(EXTRA_TITLE, title)
            try {
                ctx.startForegroundService(i)
            } catch (e: Exception) {
                // Android bazı durumlarda arka plandan başlatmaya izin vermez; bildirim yine de gösterilmiştir.
            }
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Ezan"
        Notifications.createChannel(this)

        val stopPi = PendingIntent.getService(
            this, 4, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = Notification.Builder(this, Notifications.CHANNEL_ADHAN)
            .setSmallIcon(R.drawable.ic_stat_hilal)
            .setContentTitle("$title · Ezan okunuyor")
            .setContentText("Durdurmak için dokun")
            .setContentIntent(stopPi)
            .setDeleteIntent(stopPi)
            .addAction(Notification.Action.Builder(null, "Durdur", stopPi).build())
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
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
            p.setOnErrorListener { _, _, _ -> stopEverything(); true }
            p.prepareAsync()
            player = p
        } catch (e: Exception) {
            stopEverything()
        }
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
