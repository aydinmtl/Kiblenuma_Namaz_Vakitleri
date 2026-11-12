package com.aydinmtl.namazvakitleri

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import org.json.JSONArray
import org.json.JSONObject

/**
 * Namaz oyununun 3B sahnesi.
 *
 * Çizimin tamamı assets/namazoyun3d/ klasöründeki dosyalardadır (kodla üretilmiş 3B çocuk,
 * abdesthane ve namaz odası; WebGL2 ile çizilir). Bu görünüm Android'in kendi WebView'ini
 * açar ve oyunla o sayfa arasında köprü olur. Yeni kütüphane yok, internet gerekmez.
 *
 * Dışarıya sunduğu işlevler eski 2B görünümle ([PrayerFigureView]) aynıdır; böylece
 * PrayerGameActivity hangisiyle çalıştığını bilmek zorunda kalmaz.
 *
 * Telefon 3B çizimi desteklemiyorsa (çok eski ya da WebView'siz cihaz) kendiliğinden
 * eski 2B çizime döner.
 */
class PrayerStageView(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs) {

    /** true: Zeynep, false: Ahmet. Abdest bölümünü her zaman Ahmet alır; Zeynep namaz bölümünde görünür. */
    var girl = false
        set(v) {
            field = v
            flat?.girl = v && lastScene != PrayerChapter.ABDEST
            js("N3.kiz($v)")
        }

    /** Okuma sırasında her yeni kelimede çağrılır: (okunan dua, kaçıncı kelimeye gelindi). */
    var onRecite: ((Recitation, Int) -> Unit)? = null
        set(v) {
            field = v
            flat?.onRecite = v
        }

    /** Figür balonda bir söz söylemeye başlayınca çağrılır ("Allâhu Ekber"). Ses çalmak için. */
    var onCaption: ((String) -> Unit)? = null

    /** Okuma dokunularak atlanınca çağrılır (çalan ses sussun diye). */
    var onSkip: (() -> Unit)? = null

    /** Bir duanın ses kaydı varsa süresini (ms) verir; okuma o kadar sürer. Kayıt yoksa null. */
    var reciteMs: ((Recitation) -> Long?)? = null

    private var web: WebView? = null
    private var flat: PrayerFigureView? = null // 3B çalışmazsa kullanılan 2B çizim
    private var ready = false
    private val pending = ArrayList<String>() // sayfa hazır olmadan gelen komutlar
    private val main = Handler(Looper.getMainLooper())

    // Son durum: 2B çizime dönmek gerekirse kaldığı yerden sürsün
    private var lastPose = "a_sit"
    private var lastScene = PrayerChapter.ABDEST
    private var playId = 0
    private var playMoves: List<Move> = emptyList()
    private var playDone: (() -> Unit)? = null

    init {
        setBackgroundColor(context.getColor(R.color.bg))
        try {
            startWeb()
        } catch (e: Throwable) {
            // Cihazda WebView yoksa ya da açılamıyorsa
            useFlat()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun startWeb() {
        val w = WebView(context)
        w.setBackgroundColor(context.getColor(R.color.bg))
        // Sahne JavaScript ile çizilir; yalnızca uygulamanın kendi dosyası açılır, internete çıkılmaz.
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false // android_asset bundan etkilenmez; başka dosya okunamaz
        w.settings.allowContentAccess = false
        w.settings.domStorageEnabled = false
        w.isVerticalScrollBarEnabled = false
        w.isHorizontalScrollBarEnabled = false
        w.overScrollMode = OVER_SCROLL_NEVER
        w.webViewClient = object : WebViewClient() {
            // Sayfadan başka bir adrese gidilmesine izin verme
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = true
        }
        w.addJavascriptInterface(Bridge(), "Android")
        // Sahneye dokununca okuma hemen biter (sabırsız küçükler için)
        w.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_UP) fastForward()
            false
        }
        addView(w, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        web = w
        w.loadUrl("file:///android_asset/namazoyun3d/index.html")
    }

    /** Sayfadan gelen bildirimler. Arka plan iş parçacığında çağrılır; ana iş parçacığına aktarılır. */
    private inner class Bridge {
        @JavascriptInterface
        fun hazir() = main.post {
            ready = true
            val w = web ?: return@post
            for (c in pending) w.evaluateJavascript(c, null)
            pending.clear()
        }

        @JavascriptInterface
        fun okuma(moveIndex: Int, words: Int) = main.post {
            val r = playMoves.getOrNull(moveIndex)?.recite ?: return@post
            onRecite?.invoke(r, words)
        }

        @JavascriptInterface
        fun soz(moveIndex: Int) = main.post {
            val c = playMoves.getOrNull(moveIndex)?.caption ?: return@post
            onCaption?.invoke(c)
        }

        @JavascriptInterface
        fun bitti(id: Int) = main.post {
            if (id != playId) return@post
            lastPose = playMoves.lastOrNull()?.pose ?: lastPose
            val cb = playDone
            playDone = null
            playMoves = emptyList()
            cb?.invoke()
        }

        @JavascriptInterface
        fun hata(msg: String) = main.post { useFlat() }
    }

    private fun js(code: String) {
        val w = web ?: return
        if (ready) w.evaluateJavascript(code, null) else pending.add(code)
    }

    /** 3B çizim açılamadı: eski 2B çizime geç ve kalınan yerden devam et. */
    private fun useFlat() {
        if (flat != null) return
        web?.let { removeView(it); it.destroy() }
        web = null
        pending.clear()
        val f = PrayerFigureView(context, null)
        f.girl = girl && lastScene != PrayerChapter.ABDEST
        f.onRecite = onRecite
        f.setOnClickListener { f.fastForward() }
        addView(f, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        flat = f
        f.show(lastPose, lastScene)
        val cb = playDone
        if (cb != null) {
            playDone = null
            f.play(playMoves, lastScene, null, cb)
        }
    }

    // ---------------- Dışarıdan çağrılanlar ----------------

    /** Figürü hemen bir duruşa ve sahneye koyar (canlandırmasız; oyuna devam ederken). */
    fun show(poseName: String, sceneNow: PrayerChapter) {
        lastPose = poseName
        lastScene = sceneNow
        playDone = null
        playMoves = emptyList()
        playId++
        flat?.girl = girl && sceneNow != PrayerChapter.ABDEST
        flat?.show(poseName, sceneNow)
        js("N3.goster(${JSONObject.quote(poseName)}, ${JSONObject.quote(sceneNow.name)})")
    }

    /**
     * Doğru cevaptan sonra figürü hareket ettirir. Sahne değişiyorsa ekran kısa bir an
     * kararıp yeni sahne açılır. Bitince [part] üzerinde ışıltı belirir ve [done] çağrılır.
     */
    fun play(list: List<Move>, newScene: PrayerChapter, part: BodyPart?, done: () -> Unit) {
        lastScene = newScene
        val f = flat
        if (f != null) {
            f.girl = girl && newScene != PrayerChapter.ABDEST
            f.play(list, newScene, part, done)
            return
        }
        playId++
        playMoves = list
        playDone = done
        val arr = JSONArray()
        for (m in list) {
            val o = JSONObject()
            o.put("pose", m.pose)
            // Ses kaydı olan okuma, kaydın uzunluğu kadar sürer; yoksa kelime sayısına göre
            o.put("ms", m.recite?.let { reciteMs?.invoke(it) } ?: m.ms)
            if (m.caption != null) o.put("caption", m.caption)
            if (m.water) o.put("water", true)
            m.recite?.let { r ->
                o.put("recite", r.words.size)
                o.put("words", JSONArray(r.words))
            }
            arr.put(o)
        }
        val partJs = if (part == null) "null" else JSONObject.quote(part.name)
        js("N3.oynat(${arr}, ${JSONObject.quote(newScene.name)}, $partJs, $playId)")
    }

    /** Okuma sürerken dokunulursa okumayı hemen bitirir. */
    fun fastForward() {
        onSkip?.invoke()
        flat?.fastForward()
        js("N3.hizli()")
    }

    /** Yanlış cevap: figür başını hafifçe sallar. */
    fun oops() {
        flat?.oops()
        js("N3.yanlis()")
    }

    /** Namaz tamamlandı: konfeti. */
    fun celebrate() {
        flat?.celebrate()
        js("N3.kutla()")
    }

    /** Ekrandan çıkınca çizimi durdur (pil ve ısınma için), dönünce devam et. */
    fun pause() { web?.onPause() }
    fun resume() { web?.onResume() }
    fun destroy() {
        main.removeCallbacksAndMessages(null)
        web?.let { removeView(it); it.destroy() }
        web = null
    }

    // Sahne 5:4 oranındadır (genişliğe göre yükseklik)
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = if (flat != null) (w * PrayerFigureView.H / PrayerFigureView.W).toInt() else w * 4 / 5
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
        )
    }
}
