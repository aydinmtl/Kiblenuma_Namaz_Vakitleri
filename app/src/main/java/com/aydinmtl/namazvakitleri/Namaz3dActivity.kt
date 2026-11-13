package com.aydinmtl.namazvakitleri

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * "Namazı 3B izle" ekranı.
 *
 * Canlandırmanın tamamı assets/namaz3d/index.html dosyasındadır (kodla üretilmiş 3B sahne,
 * WebGL2 ile çizilir). Bu ekran yalnızca Android'in kendi WebView'ini açıp o dosyayı yükler;
 * yeni bir kütüphane eklenmez, internet gerekmez.
 */
class Namaz3dActivity : LangActivity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this).apply { setBackgroundColor(Color.parseColor("#0D0B09")) }
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0B09"))
            // Sahne JavaScript ile çizildiği için gerekli; yalnızca uygulamanın kendi dosyası açılır.
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.domStorageEnabled = false
        }
        root.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        // Durum çubuğu ve gezinme çubuğu sahnenin düğmelerini örtmesin
        Ui.fitSystemBars(this, root)
        // "app" parametresi: video kayıt düğmesini gizler (WebView'de indirme yok)
        web.loadUrl("file:///android_asset/namaz3d/index.html?app=1")
    }

    // Ekrandan çıkınca çizimi durdur (pil ve ısınma için), dönünce devam et
    override fun onPause() { web.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
    override fun onDestroy() { web.destroy(); super.onDestroy() }
}
