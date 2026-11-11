package com.aydinmtl.namazvakitleri

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Aile ağacı çizimi. [stage]: 0 Tohum, 1 Filiz, 2 Fidan, 3 Genç ağaç,
 * 4 Çiçekli ağaç, 5 Meyveli ağaç, 6 Ulu çınar.
 */
class TreeView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    var stage = 0

    private val dp = resources.displayMetrics.density
    private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3B2F25.toInt() }
    private val grass = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E5E3E.toInt() }
    private val trunk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7A5534.toInt() }
    private val trunkStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF7A5534.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND
    }
    private val leafDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E7D4F.toInt() }
    private val leafLight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF46A36A.toInt() }
    private val flower = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF4C6D7.toInt() }
    private val fruit = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8A13A.toInt() }
    private val seed = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC9A46A.toInt() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val groundY = h - 28 * dp

        // Toprak ve çimen
        canvas.drawOval(RectF(cx - w * 0.38f, groundY - 6 * dp, cx + w * 0.38f, groundY + 18 * dp), ground)
        canvas.drawOval(RectF(cx - w * 0.36f, groundY - 9 * dp, cx + w * 0.36f, groundY + 6 * dp), grass)

        when (stage) {
            0 -> { // Tohum
                canvas.drawOval(RectF(cx - 9 * dp, groundY - 8 * dp, cx + 9 * dp, groundY + 4 * dp), seed)
            }
            1 -> { // Filiz: küçük gövde + iki yaprak
                trunkStroke.strokeWidth = 4 * dp
                canvas.drawLine(cx, groundY, cx, groundY - 34 * dp, trunkStroke)
                leaf(canvas, cx, groundY - 30 * dp, -40f, 18 * dp)
                leaf(canvas, cx, groundY - 24 * dp, 220f, 16 * dp)
            }
            2 -> { // Fidan
                trunkStroke.strokeWidth = 6 * dp
                canvas.drawLine(cx, groundY, cx, groundY - 70 * dp, trunkStroke)
                for (i in 0 until 6) {
                    val y = groundY - 30 * dp - i * 8 * dp
                    leaf(canvas, cx, y, if (i % 2 == 0) -35f else 215f, 20 * dp)
                }
            }
            else -> drawTree(canvas, cx, groundY, h)
        }
    }

    /** 3 ve üzeri aşamalar: gövde + dallar + taç; çiçek ve meyve sonraki aşamalarda. */
    private fun drawTree(canvas: Canvas, cx: Float, groundY: Float, h: Float) {
        val size = when (stage) { 3 -> 0.62f; 4 -> 0.74f; 5 -> 0.84f; else -> 0.95f }
        val treeH = (groundY - 12 * dp) * size
        val trunkW = (10 + stage * 3) * dp
        val crownR = min(width * 0.36f, treeH * 0.42f)
        val crownY = groundY - treeH + crownR

        // Gövde (aşağıda geniş, yukarıda dar)
        val p = Path().apply {
            moveTo(cx - trunkW, groundY)
            lineTo(cx - trunkW * 0.45f, crownY)
            lineTo(cx + trunkW * 0.45f, crownY)
            lineTo(cx + trunkW, groundY)
            close()
        }
        canvas.drawPath(p, trunk)
        trunkStroke.strokeWidth = trunkW * 0.35f
        canvas.drawLine(cx, crownY + crownR * 0.4f, cx - crownR * 0.55f, crownY - crownR * 0.1f, trunkStroke)
        canvas.drawLine(cx, crownY + crownR * 0.5f, cx + crownR * 0.6f, crownY, trunkStroke)

        // Taç: üst üste yuvarlaklar
        val blobs = listOf(
            Triple(0f, 0f, 1f), Triple(-0.6f, 0.2f, 0.7f), Triple(0.6f, 0.2f, 0.7f),
            Triple(-0.35f, -0.5f, 0.65f), Triple(0.35f, -0.5f, 0.65f), Triple(0f, 0.45f, 0.6f),
        )
        for ((i, b) in blobs.withIndex()) {
            canvas.drawCircle(cx + b.first * crownR, crownY + b.second * crownR, b.third * crownR * 0.75f,
                if (i % 2 == 0) leafDark else leafLight)
        }

        // Çiçekler (4+) ve meyveler (5+)
        if (stage >= 4) dots(canvas, cx, crownY, crownR, 14, 4 * dp, flower, 0.3)
        if (stage >= 5) dots(canvas, cx, crownY, crownR, if (stage >= 6) 16 else 10, 6 * dp, fruit, 1.7)
    }

    private fun dots(canvas: Canvas, cx: Float, cy: Float, r: Float, n: Int, size: Float, paint: Paint, phase: Double) {
        for (i in 0 until n) {
            val a = phase + i * 2.399963 // altın açı: düzgün dağılım
            val d = r * 0.85f * Math.sqrt((i + 0.5) / n).toFloat()
            canvas.drawCircle(cx + (cos(a) * d).toFloat(), cy + (sin(a) * d).toFloat(), size, paint)
        }
    }

    private fun leaf(canvas: Canvas, x: Float, y: Float, angle: Float, len: Float) {
        canvas.save()
        canvas.rotate(angle, x, y)
        canvas.drawOval(RectF(x, y - len * 0.22f, x + len, y + len * 0.22f), leafLight)
        canvas.restore()
    }
}
