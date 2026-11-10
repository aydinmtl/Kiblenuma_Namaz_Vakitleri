package com.aydinmtl.namazvakitleri

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pusula çizimi. [azimuth]: telefonun baktığı yön (kuzeyden saat yönünde derece).
 * [qibla]: Kâbe'nin yönü (kuzeyden saat yönünde derece), konum bilinmiyorsa null.
 */
class QiblaView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var azimuth = 0f
    var qibla: Float? = null

    private val dp = resources.displayMetrics.density
    private val gold = context.getColor(R.color.gold)
    private val green = 0xFF4CD787.toInt()

    private val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * dp; color = 0x66FFFFFF
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2 * dp; color = 0x99FFFFFF.toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.text); textSize = 20 * dp; textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val northPaint = Paint(textPaint).apply { color = 0xFFFF6E6E.toInt() }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6 * dp; strokeCap = Paint.Cap.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = context.getColor(R.color.text)
    }

    /** Kıble ile telefonun yönü arasındaki fark (-180..180). */
    fun offset(): Float? = qibla?.let { ((it - azimuth + 540f) % 360f) - 180f }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = min(cx, cy) - 24 * dp
        if (r <= 0) return

        // Üstte sabit işaret: telefonun baktığı yön.
        val marker = Path().apply {
            moveTo(cx, cy - r + 2 * dp)
            lineTo(cx - 10 * dp, cy - r - 16 * dp)
            lineTo(cx + 10 * dp, cy - r - 16 * dp)
            close()
        }
        canvas.drawPath(marker, markerPaint)

        canvas.save()
        canvas.rotate(-azimuth, cx, cy)

        canvas.drawCircle(cx, cy, r, dialPaint)
        for (deg in 0 until 360 step 10) {
            val len = if (deg % 30 == 0) 16 * dp else 8 * dp
            val a = Math.toRadians(deg.toDouble())
            val sx = cx + r * sin(a).toFloat()
            val sy = cy - r * cos(a).toFloat()
            val ex = cx + (r - len) * sin(a).toFloat()
            val ey = cy - (r - len) * cos(a).toFloat()
            canvas.drawLine(sx, sy, ex, ey, tickPaint)
        }

        val labels = arrayOf("K", "D", "G", "B") // Kuzey, Doğu, Güney, Batı
        val lr = r - 40 * dp
        for (i in 0..3) {
            val a = Math.toRadians(i * 90.0)
            val x = cx + lr * sin(a).toFloat()
            val y = cy - lr * cos(a).toFloat() + textPaint.textSize / 3
            canvas.drawText(labels[i], x, y, if (i == 0) northPaint else textPaint)
        }

        val q = qibla
        if (q != null) {
            val aligned = abs(offset() ?: 180f) < 3f
            val color = if (aligned) green else gold
            arrowPaint.color = color
            fillPaint.color = color

            canvas.save()
            canvas.rotate(q, cx, cy)
            val tipY = cy - r + 34 * dp
            canvas.drawLine(cx, cy + r * 0.25f, cx, tipY + 10 * dp, arrowPaint)
            val head = Path().apply {
                moveTo(cx, tipY - 6 * dp)
                lineTo(cx - 14 * dp, tipY + 16 * dp)
                lineTo(cx + 14 * dp, tipY + 16 * dp)
                close()
            }
            canvas.drawPath(head, fillPaint)
            // Kâbe'yi temsil eden küçük kare
            val s = 9 * dp
            canvas.drawRect(cx - s, tipY - 6 * dp - 2 * s - 4 * dp, cx + s, tipY - 6 * dp - 4 * dp, fillPaint)
            canvas.restore()
        }

        canvas.drawCircle(cx, cy, 6 * dp, markerPaint)
        canvas.restore()
    }
}
