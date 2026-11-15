package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.content.Context
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.WindowInsets
import java.io.IOException
import org.json.JSONException

/** Kullanıcıya olduğu gibi gösterilebilecek, seçili dilde yazılmış bir hata iletisi taşır. */
class UserMessage(message: String) : Exception(message)

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

    /**
     * Bir hatayı kullanıcıya gösterilecek kısa, seçili dildeki bir ifadeye çevirir.
     * Java'nın teknik İngilizce iletileri ("Unable to resolve host…") ekrana basılmaz.
     * İfade cümle içinde parantezle kullanılır: "Bağlantı hatası (internet bağlantısı yok)."
     */
    fun errorText(ctx: Context, e: Throwable): String = when (e) {
        is UserMessage -> e.message ?: ctx.getString(R.string.err_unexpected)
        is Diyanet.HttpError -> ctx.getString(R.string.err_server, e.code)
        is JSONException -> ctx.getString(R.string.err_bad_response)
        is IOException -> ctx.getString(R.string.err_offline) // adres çözülemedi, bağlantı kurulamadı, zaman aşımı
        else -> ctx.getString(R.string.err_unexpected)
    }

    /**
     * Kıble ekranlarındaki yön yazısı. Pusula ayarsızsa ([lowAccuracy]) kalibrasyon uyarısı renkli
     * olarak ÜSTTE durur, yön yazısı ([direction]) altında kalır. Gösterilecek bir şey yoksa null.
     */
    fun compassHint(ctx: Context, lowAccuracy: Boolean, direction: String?): CharSequence? {
        if (!lowAccuracy) return direction
        val warning = "⚠ " + ctx.getString(R.string.qb_calibrate)
        val out = SpannableStringBuilder(warning)
        out.setSpan(ForegroundColorSpan(ctx.getColor(R.color.error)), 0, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (direction != null) out.append("\n").append(direction)
        return out
    }
}
