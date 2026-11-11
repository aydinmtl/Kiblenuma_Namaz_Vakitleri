package com.aydinmtl.namazvakitleri

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

/**
 * Kamera görüntüsünün üstüne çizilir. [offset]: kıblenin, kameranın baktığı yöne göre
 * kaç derece sağda (+) ya da solda (−) olduğu. Görüş alanındaysa Kâbe işareti, değilse yön oku.
 */
class ArOverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var offset: Float? = null
    /**
     * Kameranın, telefon dik tutulunca tam yatay görüş açısı (derece). QiblaArActivity bunu
     * kameranın kendi bilgilerinden okur; okuyamazsa varsayılan kalır.
     */
    var cameraFov = ArMath.DEFAULT_CAMERA_FOV

    private val dp = resources.displayMetrics.density
    private val gold = context.getColor(R.color.gold)
    private val green = 0xFF4CD787.toInt()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3 * dp; color = 0xAAFFFFFF.toInt()
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textSize = 22 * dp; textAlign = Paint.Align.CENTER; isFakeBoldText = true
        setShadowLayer(4 * dp, 0f, 0f, 0xFF000000.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val off = offset ?: return
        val cx = width / 2f
        val cy = height / 2f

        // Ortadaki nişangâh
        canvas.drawLine(cx, cy - 30 * dp, cx, cy + 30 * dp, stroke)

        // Ekranda görünen açı: kamera görüntüsü ekranı doldurmak için kırpıldığından daha dar.
        val half = ArMath.visibleFov(cameraFov, width, height) / 2f
        if (abs(off) <= half) {
            val aligned = abs(off) < 3f
            val color = if (aligned) green else gold
            fill.color = color
            val x = ArMath.screenX(off, half, width)
            // Kâbe: siyah küp + altın kuşak
            val s = 34 * dp
            fill.color = 0xFF111111.toInt()
            canvas.drawRect(x - s, cy - s, x + s, cy + s, fill)
            fill.color = color
            canvas.drawRect(x - s, cy - s * 0.55f, x + s, cy - s * 0.35f, fill)
            stroke.color = color
            canvas.drawRect(x - s, cy - s, x + s, cy + s, stroke)
            stroke.color = 0xAAFFFFFF.toInt()
            canvas.drawText(if (aligned) "✓ Kıble" else "Kâbe", x, cy + s + 32 * dp, text)
        } else {
            // Görüş alanı dışında: kenarda ok
            fill.color = gold
            val right = off > 0
            val x = if (right) width - 36 * dp else 36 * dp
            val dir = if (right) 1 else -1
            val p = Path().apply {
                moveTo(x + dir * 24 * dp, cy)
                lineTo(x - dir * 12 * dp, cy - 28 * dp)
                lineTo(x - dir * 12 * dp, cy + 28 * dp)
                close()
            }
            canvas.drawPath(p, fill)
            canvas.drawText(
                if (right) "Sağa dön ${abs(off).toInt()}°" else "Sola dön ${abs(off).toInt()}°",
                cx, cy - 60 * dp, text,
            )
        }
    }
}
