package com.aydinmtl.namazvakitleri

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView

/**
 * "Namazı 3B izle" ekranı.
 *
 * Canlandırmanın tamamı assets/namaz3d/index.html dosyasındadır (kodla üretilmiş 3B sahne,
 * WebGL2 ile çizilir). Bu ekran yalnızca Android'in kendi WebView'ini açıp o dosyayı yükler;
 * yeni bir kütüphane eklenmez, internet gerekmez.
 *
 * WebView her telefonda her an kullanılabilir değildir (güncellenirken ya da hiç yokken açılamaz)
 * ve sayfayı çizen ayrı süreç çökebilir. İki durumda da ekran kapanmak yerine bir yazı gösterir.
 */
class Namaz3dActivity : LangActivity() {

    private lateinit var root: FrameLayout
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0D0B09")) }
        setContentView(root)
        // Durum çubuğu ve gezinme çubuğu sahnenin düğmelerini örtmesin
        Ui.fitSystemBars(this, root)
        startWeb()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startWeb() {
        val w = try {
            WebView(this)
        } catch (e: Throwable) {
            // Telefonda WebView yok ya da şu an açılamıyor
            showProblem(R.string.n3d_unavailable, retry = false)
            return
        }
        w.setBackgroundColor(Color.parseColor("#0D0B09"))
        // Sahne JavaScript ile çizildiği için gerekli; yalnızca uygulamanın kendi dosyası açılır.
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false
        w.settings.domStorageEnabled = false
        w.webViewClient = object : WebViewClient() {
            // Uygulamanın kendi sayfası dışında bir adrese gidilmez (sayfa kendini yeniden yükleyebilir).
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith(PAGE)

            // Sayfayı çizen süreç çöktü ya da sistem onu kapattı. Ele alınmazsa Android uygulamanın
            // tamamını kapatır. Ölü WebView atılır, kullanıcıya "yeniden aç" yazısı gösterilir.
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                root.post {
                    dropWeb()
                    showProblem(R.string.n3d_stopped, retry = true)
                }
                return true
            }
        }
        root.addView(w, FrameLayout.LayoutParams(-1, -1))
        web = w
        // "app" parametresi: video kayıt düğmesini gizler (WebView'de indirme yok)
        w.loadUrl("$PAGE?app=1&lang=${Lang.code(this)}")
    }

    private fun dropWeb() {
        web?.let {
            root.removeView(it)
            it.destroy()
        }
        web = null
    }

    /** Sahne yerine ortada bir yazı; [retry] ise yazıya dokununca sahne yeniden açılır. */
    private fun showProblem(textId: Int, retry: Boolean) {
        val tv = TextView(this)
        tv.setText(textId)
        tv.textSize = 17f
        tv.setTextColor(getColor(R.color.text))
        tv.gravity = Gravity.CENTER
        val pad = (24 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad, pad, pad)
        if (retry) {
            tv.setOnClickListener {
                root.removeView(tv)
                startWeb()
            }
        }
        root.addView(tv, FrameLayout.LayoutParams(-1, -1))
    }

    // Ekrandan çıkınca çizimi durdur (pil ve ısınma için), dönünce devam et
    override fun onPause() {
        web?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onDestroy() {
        dropWeb()
        super.onDestroy()
    }

    private companion object {
        const val PAGE = "file:///android_asset/namaz3d/index.html"
    }
}
