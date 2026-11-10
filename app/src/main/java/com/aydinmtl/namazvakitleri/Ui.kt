package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets

object Ui {
    /**
     * Android 15 uygulamayı ekranın tamamına (durum çubuğunun altına) çizer.
     * Bu fonksiyon içeriği durum ve gezinme çubuklarının dışında tutar.
     */
    fun fitSystemBars(activity: Activity, root: View) {
        if (Build.VERSION.SDK_INT < 30) return
        activity.window.setDecorFitsSystemWindows(false)
        val l = root.paddingLeft
        val t = root.paddingTop
        val r = root.paddingRight
        val b = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
        root.requestApplyInsets()
    }
}
