package com.aydinmtl.namazvakitleri

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer

/**
 * Namaz oyunundaki sesli okuma: figür bir dua, sure, niyet ya da "Allâhu Ekber" söylerken
 * uygulamanın içindeki ses kaydını çalar.
 *
 * Ses dosyaları uygulamayla birlikte gelir: `app/src/main/res/raw/` klasöründe, adları
 * [PrayerSounds] içinde yazılıdır (ör. `ses_fatiha.mp3`). Bir dosya yoksa o okuma sessiz geçer;
 * oyun eskisi gibi çalışmaya devam eder. Dosya listesi: docs/SES_DOSYALARI.md
 */
class PrayerVoice(private val ctx: Context) {

    private var player: MediaPlayer? = null
    private var playing: String? = null // şu an çalan dosyanın adı
    private val durations = HashMap<String, Long>() // dosya adı → süre (ms); 0: dosya yok

    /** Dosyanın kaynak numarası; dosya uygulamada yoksa 0. Ada göre arandığı için dosya eklemek kod değişikliği istemez. */
    @SuppressLint("DiscouragedApi")
    private fun resId(name: String): Int = ctx.resources.getIdentifier(name, "raw", ctx.packageName)

    /** Ses kaydının süresi (ms). Dosya yoksa ya da okunamıyorsa null. */
    fun durationMs(name: String?): Long? {
        if (name == null) return null
        val d = durations.getOrPut(name) {
            val id = resId(name)
            if (id == 0) return@getOrPut 0L
            val mmr = MediaMetadataRetriever()
            try {
                ctx.resources.openRawResourceFd(id).use { fd ->
                    mmr.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                }
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            } catch (e: Exception) {
                0L
            } finally {
                try { mmr.release() } catch (e: Exception) { /* eski sürümlerde hata verebilir */ }
            }
        }
        return if (d > 0) d else null
    }

    /** [name] adlı kaydı baştan çalar. Aynı kayıt zaten çalıyorsa dokunmaz (art arda iki "Allâhu Ekber" karesi gibi). */
    fun play(name: String?) {
        if (name == null) return
        val id = resId(name)
        if (id == 0) return
        if (playing == name && player?.isPlaying == true) return
        stop()
        val p = (try { MediaPlayer.create(ctx, id) } catch (e: Exception) { null }) ?: return
        p.setOnCompletionListener { if (player === it) stop() }
        player = p
        playing = name
        p.start()
    }

    /** Çalan kaydı susturur (ekrandan çıkınca ya da okuma atlanınca). */
    fun stop() {
        val p = player ?: return
        player = null
        playing = null
        try { p.stop() } catch (e: Exception) { /* zaten durmuş olabilir */ }
        p.release()
    }
}
