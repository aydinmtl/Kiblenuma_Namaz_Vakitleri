package com.aydinmtl.namazvakitleri

import android.content.Context
import android.speech.tts.TextToSpeech

/**
 * Sesli okuma: soruyu ve şıkları telefonun kendi metin okuma motoruyla, seçili dilde okur
 * (okuma bilmeyen küçükler için). Ek kütüphane yoktur; motor ilk kullanımda açılır.
 * Bilgi Yarışması ve Namaz Öğreniyorum ekranları ortak kullanır.
 *
 * [utteranceId]: Android'in okumaları ayırt etmek için istediği ad.
 * [onUnavailable]: telefonda metin okuma motoru ya da seçili dilin sesi yoksa çağrılır
 * (ekran kullanıcıya bunu söyler; düğme sessizce hiçbir şey yapmıyor gibi kalmaz).
 */
class Speaker(
    private val ctx: Context,
    private val utteranceId: String,
    private val onUnavailable: () -> Unit,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    /** [text] yazısını okur; o sırada okunan bir şey varsa onun yerini alır. */
    fun speak(text: String) {
        val engine = tts
        if (engine != null && ready) {
            pending = null
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            return
        }
        pending = text
        if (engine != null) return // motor hâlâ açılıyor; hazır olunca okunur

        // Motor açılınca Android sonucu aşağıdaki blokla bildirir. Telefonda hiç motor yoksa bu
        // bildirim, TextToSpeech(...) satırı daha bitmeden gelebilir; o an motor henüz [tts]'e
        // yazılmamıştır. Bu durumda sonuç saklanır ([early]) ve hemen ardından işlenir.
        // (Eskiden bu erken bildirim kayboluyor, düğme bir daha hiçbir şey yapmıyordu.)
        val holder = arrayOfNulls<TextToSpeech>(1)
        var early: Int? = null
        val created = TextToSpeech(ctx) { status ->
            val t = holder[0]
            if (t == null) {
                early = status
            } else {
                onInit(t, status)
            }
        }
        holder[0] = created
        tts = created
        early?.let { onInit(created, it) }
    }

    private fun onInit(t: TextToSpeech, status: Int) {
        if (tts !== t) return // bu arada kapatıldı (bkz. shutdown)
        val lang = if (status == TextToSpeech.SUCCESS) t.setLanguage(Lang.locale(ctx)) else TextToSpeech.LANG_NOT_SUPPORTED
        if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Motor yok ya da seçili dilin sesi yüklü değil: sonraki dokunuşta yeniden denensin.
            pending = null
            ready = false
            t.shutdown()
            tts = null
            onUnavailable()
            return
        }
        ready = true
        pending?.let { t.speak(it, TextToSpeech.QUEUE_FLUSH, null, utteranceId) }
        pending = null
    }

    /** Okumayı keser. Bekleyen okuma da iptal edilir: motor geç açılsa bile sonradan başlamaz. */
    fun stop() {
        pending = null
        tts?.stop()
    }

    /** Ekran kapanırken: motoru bırakır. */
    fun shutdown() {
        pending = null
        ready = false
        tts?.shutdown()
        tts = null
    }
}
