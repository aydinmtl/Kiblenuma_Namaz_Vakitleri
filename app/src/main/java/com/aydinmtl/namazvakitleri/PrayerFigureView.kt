package com.aydinmtl.namazvakitleri

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Namaz oyunundaki sahne ve canlandırmalı çocuk figürü. Ek kütüphane yok: her şey Android'in
 * kendi çizim araçlarıyla (Canvas) çizilir.
 *
 * - Sahne 200 x 125 birimlik bir alandır; görünüm genişliğine göre büyütülür.
 * - İki sahne var: abdesthane (tabure, musluk, çini duvar) ve namaz odası (seccade, mihrap,
 *   sabah vakti penceresi). Kıbleye dönünce sahneler yumuşakça birbirine geçer.
 * - Figür [Poses] içindeki duruşlar arasında yumuşakça hareket eder ([play]).
 * - Su damlaları, yıkanan yerde ışıltılar, sonda konfeti; göz kırpma ve yüz ifadesi.
 */
class PrayerFigureView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    enum class Face { NORMAL, HAPPY, OOPS }

    /** true: kız çocuğu (başörtülü, elbiseli), false: erkek çocuğu (takkeli). */
    var girl = false
        set(v) {
            field = v
            currentPose = Poses.get(restName, v)
            invalidate()
        }

    // ---------------- Durum ----------------
    private var restName = "a_sit"
    private var currentPose: Pose = Poses.get("a_sit", false)
    private var scene = PrayerChapter.ABDEST
    private var prevScene = PrayerChapter.ABDEST
    private var sceneMix = 1f // 0: önceki sahne, 1: yeni sahne
    private var sceneStart = 0L

    private var moves: List<Move> = emptyList()
    private var moveIdx = 0
    private var segStart = 0L
    private var fromPose: Pose = currentPose
    private var onDone: (() -> Unit)? = null
    private var sparklePart: BodyPart? = null
    private var caption: String? = null
    private var water = false

    private var face = Face.NORMAL
    private var faceUntil = 0L
    private var nextBlink = 0L
    private var shakeStart = 0L

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val kind: Int, val color: Int,
        var rot: Float = 0f, val vr: Float = 0f, val size: Float = 1f,
    )

    private val particles = ArrayList<Particle>()
    private var lastFrame = 0L
    private val rnd = Random(7)

    // ---------------- Boyalar ----------------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val rect = RectF()

    companion object {
        const val W = 200f
        const val H = 125f
        const val GROUND = 112f
        private const val SKIN = 0xFFF2C29B.toInt()
        private const val SKIN_DARK = 0xFFD9A47C.toInt()
        private const val OUTLINE = 0xFF3B2A22.toInt()
        private const val EYE = 0xFF2B1D16.toInt()
    }

    // Erkek / kız renkleri
    private val cloth get() = if (girl) 0xFF9B7BD4.toInt() else 0xFFF6F3EA.toInt()
    private val clothDark get() = if (girl) 0xFF7A5CB3.toInt() else 0xFFD9D4C6.toInt()
    private val pants get() = if (girl) 0xFF7A5CB3.toInt() else 0xFF4D72A8.toInt()
    private val pantsDark get() = if (girl) 0xFF654A99.toInt() else 0xFF3C5B88.toInt()

    // ---------------- Dışarıdan çağrılanlar ----------------

    /** Figürü hemen bir duruşa ve sahneye koyar (canlandırmasız; oyuna devam ederken). */
    fun show(poseName: String, sceneNow: PrayerChapter) {
        restName = poseName
        currentPose = Poses.get(poseName, girl)
        moves = emptyList()
        scene = sceneNow
        prevScene = sceneNow
        sceneMix = 1f
        caption = null
        water = false
        invalidate()
    }

    /**
     * Doğru cevaptan sonra figürü hareket ettirir. [newScene] öncekinden farklıysa sahne
     * yumuşakça değişir. Bitince [part] üzerinde ışıltı belirir ve [done] çağrılır.
     */
    fun play(list: List<Move>, newScene: PrayerChapter, part: BodyPart?, done: () -> Unit) {
        val now = SystemClock.uptimeMillis()
        if (newScene != scene) {
            prevScene = scene
            scene = newScene
            sceneMix = 0f
            sceneStart = now
        }
        moves = list
        moveIdx = 0
        segStart = now
        fromPose = currentPose
        onDone = done
        sparklePart = part
        face = Face.HAPPY
        faceUntil = Long.MAX_VALUE
        invalidate()
    }

    /** Yanlış cevap: figür başını hafifçe sallar, yüzü "hımm" olur. */
    fun oops() {
        val now = SystemClock.uptimeMillis()
        face = Face.OOPS
        faceUntil = now + 1300
        shakeStart = now
        invalidate()
    }

    /** Namaz tamamlandı: konfeti ve bol ışıltı. */
    fun celebrate() {
        val colors = intArrayOf(0xFFE8C15A.toInt(), 0xFF6FD3A0.toInt(), 0xFFF28CB0.toInt(), 0xFF7FB8F0.toInt(), 0xFFFFFFFF.toInt())
        repeat(140) {
            particles.add(
                Particle(
                    rnd.nextFloat() * W, -rnd.nextFloat() * 60f, (rnd.nextFloat() - 0.5f) * 30f, 20f + rnd.nextFloat() * 30f,
                    0f, 4f + rnd.nextFloat() * 2f, 2, colors[it % colors.size],
                    rnd.nextFloat() * 360f, (rnd.nextFloat() - 0.5f) * 600f, 1.2f + rnd.nextFloat(),
                ),
            )
        }
        sparkle(BodyPart.ALL, 40)
        face = Face.HAPPY
        faceUntil = SystemClock.uptimeMillis() + 5000
        invalidate()
    }

    // ---------------- Ölçü ----------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, (w * H / W).toInt())
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        lastFrame = 0L
        if (visibility == VISIBLE) invalidate()
    }

    // ---------------- Her kare ----------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 0f else min(0.05f, (now - lastFrame) / 1000f)
        lastFrame = now
        update(now, dt)

        val s = width / W
        canvas.save()
        canvas.scale(s, s)
        // Arka plan: sahne geçişinde iki sahne üst üste karışır
        if (sceneMix < 1f) {
            drawScene(canvas, prevScene)
            val c = canvas.saveLayerAlpha(0f, 0f, W, H, (255 * sceneMix).toInt())
            drawScene(canvas, scene)
            canvas.restoreToCount(c)
        } else {
            drawScene(canvas, scene)
        }

        val hipX = figureX()
        val sk = Skeleton.of(currentPose)
        canvas.save()
        canvas.translate(hipX, GROUND)
        if (water) drawWaterStream(canvas)
        drawFigure(canvas, sk, now)
        canvas.restore()

        drawParticles(canvas)
        caption?.let { drawCaption(canvas, it, hipX + sk.head.x, GROUND + sk.head.y - Skeleton.HEAD_R) }
        canvas.restore()

        // Sahne görünür olduğu sürece bir sonraki kareyi iste (canlandırma, su, göz kırpma)
        if (windowVisibility == VISIBLE) postInvalidateOnAnimation()
    }

    /** Figürün kalçasının sahnedeki yeri (sahneye göre; geçişte kayarak değişir). */
    private fun figureX(): Float {
        fun x(c: PrayerChapter) = if (c == PrayerChapter.ABDEST) 70f else 62f
        return x(prevScene) + (x(scene) - x(prevScene)) * smooth(sceneMix)
    }

    private fun smooth(f: Float): Float {
        val x = f.coerceIn(0f, 1f)
        return x * x * (3 - 2 * x)
    }

    private fun update(now: Long, dt: Float) {
        // Sahne geçişi (1,2 saniye)
        if (sceneMix < 1f) sceneMix = min(1f, (now - sceneStart) / 1200f)

        // Hareketler
        if (moveIdx < moves.size) {
            val m = moves[moveIdx]
            val target = Poses.get(m.pose, girl)
            val f = ((now - segStart).toFloat() / m.ms).coerceIn(0f, 1f)
            currentPose = Pose.lerp(fromPose, target, smooth(f))
            caption = m.caption
            water = m.water
            if (f >= 1f) {
                fromPose = target
                restName = m.pose
                moveIdx++
                segStart = now
                if (moveIdx >= moves.size) {
                    water = false
                    caption = moves.lastOrNull()?.caption
                    sparklePart?.let { sparkle(it, if (it == BodyPart.ALL) 36 else 18) }
                    faceUntil = now + 1500
                    val cb = onDone
                    onDone = null
                    // Balon biraz daha kalsın, sonra kaybolsun
                    postDelayed({ if (moveIdx >= moves.size) caption = null }, 1600)
                    if (cb != null) post { cb() }
                }
            }
        }

        if (now > faceUntil) face = Face.NORMAL

        // Su damlaları musluktan dökülür
        if (water && rnd.nextFloat() < 0.9f) {
            repeat(2) {
                particles.add(
                    Particle(
                        figureX() + 38f + (rnd.nextFloat() - 0.5f) * 1.6f, 62f, (rnd.nextFloat() - 0.5f) * 3f, 30f + rnd.nextFloat() * 10f,
                        0f, 1.2f, 0, 0xCCBFE8FF.toInt(), size = 0.6f + rnd.nextFloat() * 0.5f,
                    ),
                )
            }
        }

        // Parçacıkları hareket ettir
        val iter = particles.iterator()
        while (iter.hasNext()) {
            val p = iter.next()
            p.life += dt
            when (p.kind) {
                0 -> { // su: yerçekimi, yere değince sıçrar ve kaybolur
                    p.vy += 160f * dt
                    if (p.y >= GROUND - 1f && p.vy > 0f) {
                        p.vy = -p.vy * 0.25f
                        p.vx = (rnd.nextFloat() - 0.5f) * 20f
                        p.life = max(p.life, p.maxLife - 0.25f)
                    }
                }
                1 -> { p.vy -= 4f * dt } // ışıltı: hafifçe yükselir
                2 -> { // konfeti: yavaş düşer, sallanır
                    p.vx += sin(p.life * 4f + p.size) * 6f * dt
                    p.rot += p.vr * dt
                }
            }
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (p.life >= p.maxLife || p.y > H + 10f) iter.remove()
        }
    }

    /** Yıkanan yerde altın ve beyaz ışıltılar. */
    private fun sparkle(part: BodyPart, count: Int) {
        val sk = Skeleton.of(currentPose)
        val hx = figureX()
        val at: List<Pt> = when (part) {
            BodyPart.HANDS -> listOf(sk.nHand, sk.fHand)
            BodyPart.MOUTH, BodyPart.NOSE, BodyPart.FACE, BodyPart.EARS, BodyPart.HEAD -> listOf(sk.head)
            BodyPart.R_ARM -> listOf(sk.fElbow, sk.fHand)
            BodyPart.L_ARM -> listOf(sk.nElbow, sk.nHand)
            BodyPart.R_FOOT -> listOf(sk.fAnkle, sk.fToe)
            BodyPart.L_FOOT -> listOf(sk.nAnkle, sk.nToe)
            BodyPart.ALL -> sk.points()
        }
        repeat(count) { i ->
            val p = at[i % at.size]
            val spread = if (part == BodyPart.ALL) 8f else 6f
            particles.add(
                Particle(
                    hx + p.x + (rnd.nextFloat() - 0.5f) * spread * 2, GROUND + p.y + (rnd.nextFloat() - 0.5f) * spread * 2,
                    (rnd.nextFloat() - 0.5f) * 6f, -4f - rnd.nextFloat() * 6f,
                    -i * 0.03f, 1.1f + rnd.nextFloat() * 0.6f, 1,
                    if (i % 3 == 0) 0xFFFFFFFF.toInt() else 0xFFFFE08A.toInt(), size = 1.2f + rnd.nextFloat() * 1.4f,
                ),
            )
        }
    }

    // ---------------- Sahneler ----------------

    private fun drawScene(c: Canvas, which: PrayerChapter) {
        if (which == PrayerChapter.ABDEST) drawAbdestScene(c) else drawPrayerScene(c)
    }

    private fun drawAbdestScene(c: Canvas) {
        // Çini duvar
        fill.shader = LinearGradient(0f, 0f, 0f, GROUND, 0xFF2F6A70.toInt(), 0xFF22504F.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, GROUND, fill)
        fill.shader = null
        stroke.strokeWidth = 0.35f
        stroke.color = 0x33FFFFFF
        var y = 8f
        while (y < GROUND) { c.drawLine(0f, y, W, y, stroke); y += 10f }
        var x: Float
        var row = 0
        y = 0f
        while (y < GROUND) {
            x = if (row % 2 == 0) 0f else 5f
            while (x < W) { c.drawLine(x, y, x, min(y + 10f, GROUND), stroke); x += 10f }
            y += 10f
            row++
        }
        // Çini süsü: duvarda bir sıra küçük çiçek
        for (i in 0 until 20) {
            val cx = 5f + i * 10f
            fill.color = 0x55E8C15A
            c.drawCircle(cx, 33f, 1.4f, fill)
            fill.color = 0x447FD1C8
            c.drawCircle(cx, 33f, 0.6f, fill)
        }
        // Mermer zemin ve su oluğu
        fill.color = 0xFFB9C3C2.toInt()
        c.drawRect(0f, GROUND, W, H, fill)
        fill.color = 0xFF8E9D9C.toInt()
        c.drawRect(0f, GROUND, W, GROUND + 1.2f, fill)
        val fx = figureXFor(PrayerChapter.ABDEST) + 38f
        fill.color = 0xFF6F7F80.toInt()
        rect.set(fx - 14f, GROUND + 3f, fx + 14f, GROUND + 6f)
        c.drawRoundRect(rect, 1.5f, 1.5f, fill)
        stroke.color = 0xFF4E5C5D.toInt()
        stroke.strokeWidth = 0.5f
        var gx = fx - 12f
        while (gx < fx + 13f) { c.drawLine(gx, GROUND + 3.5f, gx, GROUND + 5.5f, stroke); gx += 2f }

        // Musluk: duvardan çıkan pirinç boru
        fill.color = 0xFFC9A253.toInt()
        rect.set(fx - 1.6f, 52f, W, 56f)
        c.drawRoundRect(rect, 2f, 2f, fill)
        rect.set(fx - 1.8f, 54f, fx + 1.8f, 62f)
        c.drawRoundRect(rect, 1.2f, 1.2f, fill)
        fill.color = 0xFFE3C27A.toInt()
        rect.set(fx + 3f, 48f, fx + 9f, 50.2f) // musluk kolu
        c.drawRoundRect(rect, 1f, 1f, fill)
        rect.set(fx + 5.2f, 49.5f, fx + 6.8f, 53f)
        c.drawRect(rect, fill)

        // Tabure
        val sx = figureXFor(PrayerChapter.ABDEST)
        val top = GROUND - 19.6f
        fill.color = 0xFF8B5A35.toInt()
        rect.set(sx - 10f, top, sx + 9f, top + 3f)
        c.drawRoundRect(rect, 1.2f, 1.2f, fill)
        stroke.color = 0xFF6E4527.toInt()
        stroke.strokeWidth = 2f
        c.drawLine(sx - 8f, top + 2f, sx - 9f, GROUND, stroke)
        c.drawLine(sx + 7f, top + 2f, sx + 8f, GROUND, stroke)
    }

    private fun figureXFor(c: PrayerChapter) = if (c == PrayerChapter.ABDEST) 70f else 62f

    private fun drawPrayerScene(c: Canvas) {
        // Duvar
        fill.shader = LinearGradient(0f, 0f, 0f, GROUND, 0xFF27473D.toInt(), 0xFF35594A.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, W, GROUND, fill)
        fill.shader = null

        // Pencere: sabah vakti (alaca karanlık), hilal ve yıldız
        rect.set(14f, 18f, 44f, 58f)
        fill.shader = LinearGradient(0f, 18f, 0f, 58f, 0xFF1B2A55.toInt(), 0xFFF0A766.toInt(), Shader.TileMode.CLAMP)
        path.reset()
        path.moveTo(14f, 58f); path.lineTo(14f, 30f)
        path.quadTo(14f, 18f, 29f, 16f); path.quadTo(44f, 18f, 44f, 30f)
        path.lineTo(44f, 58f); path.close()
        c.drawPath(path, fill)
        fill.shader = null
        fill.color = 0xFFFFF3C4.toInt()
        c.drawCircle(35f, 28f, 4f, fill)
        fill.color = 0xFF22325E.toInt()
        c.drawCircle(36.8f, 26.8f, 3.6f, fill)
        drawStar(c, 22f, 25f, 1.3f, 0xFFFFF3C4.toInt())
        stroke.color = 0xFFC9A253.toInt()
        stroke.strokeWidth = 1.4f
        c.drawPath(path, stroke)
        stroke.strokeWidth = 0.8f
        c.drawLine(29f, 17f, 29f, 58f, stroke)
        c.drawLine(14f, 40f, 44f, 40f, stroke)

        // Mihrap: kıble yönü (figür bu yöne bakıyor)
        val mx = 182f
        path.reset()
        path.moveTo(mx - 15f, GROUND); path.lineTo(mx - 15f, 50f)
        path.quadTo(mx - 15f, 30f, mx, 22f); path.quadTo(mx + 15f, 30f, mx + 15f, 50f)
        path.lineTo(mx + 15f, GROUND); path.close()
        fill.color = 0xFF1C3830.toInt()
        c.drawPath(path, fill)
        stroke.color = 0xFFE8C15A.toInt()
        stroke.strokeWidth = 1.6f
        c.drawPath(path, stroke)
        path.reset()
        path.moveTo(mx - 10f, GROUND); path.lineTo(mx - 10f, 54f)
        path.quadTo(mx - 10f, 38f, mx, 32f); path.quadTo(mx + 10f, 38f, mx + 10f, 54f)
        path.lineTo(mx + 10f, GROUND)
        stroke.strokeWidth = 0.6f
        c.drawPath(path, stroke)
        text.color = 0xFFE8C15A.toInt()
        text.textSize = 4.2f
        text.isFakeBoldText = true
        c.drawText("KIBLE", mx, 18f, text)
        text.isFakeBoldText = false
        drawStar(c, mx, 44f, 2.2f, 0xFFE8C15A.toInt())

        // Ahşap zemin
        fill.color = 0xFF6B4E35.toInt()
        c.drawRect(0f, GROUND, W, H, fill)
        stroke.color = 0x33000000
        stroke.strokeWidth = 0.4f
        c.drawLine(0f, GROUND + 6f, W, GROUND + 6f, stroke)

        // Seccade (yandan bakınca ince bir halı), saçaklı
        val rx0 = figureXFor(PrayerChapter.NAMAZ) - 26f
        val rx1 = figureXFor(PrayerChapter.NAMAZ) + 64f
        fill.color = 0xFFB23A3A.toInt()
        rect.set(rx0, GROUND - 0.6f, rx1, GROUND + 3.4f)
        c.drawRoundRect(rect, 0.8f, 0.8f, fill)
        fill.color = 0xFFE8C15A.toInt()
        rect.set(rx0 + 1.5f, GROUND + 0.6f, rx1 - 1.5f, GROUND + 1.4f)
        c.drawRect(rect, fill)
        stroke.color = 0xFFE8D3A0.toInt()
        stroke.strokeWidth = 0.4f
        var fx = rx0 - 2.5f
        while (fx < rx0) { c.drawLine(fx, GROUND + 0.6f, fx + 2.5f, GROUND + 1.2f, stroke); fx += 0.9f }
        fx = rx1
        while (fx < rx1 + 2.5f) { c.drawLine(fx, GROUND + 1.2f, fx + 2.5f, GROUND + 0.6f, stroke); fx += 0.9f }
    }

    /** Dört köşeli küçük yıldız (ışıltı). */
    private fun drawStar(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        path.reset()
        path.moveTo(x, y - r * 2f)
        path.quadTo(x, y, x + r * 2f, y)
        path.quadTo(x, y, x, y + r * 2f)
        path.quadTo(x, y, x - r * 2f, y)
        path.quadTo(x, y, x, y - r * 2f)
        fill.color = color
        c.drawPath(path, fill)
    }

    // ---------------- Su ----------------

    /** Musluktan akan ince su: (koordinatlar figürün kalçasına göre) */
    private fun drawWaterStream(c: Canvas) {
        val x = 38f
        fill.shader = LinearGradient(0f, 62f - GROUND, 0f, 0f, 0xDDCFEFFF.toInt(), 0x55CFEFFF, Shader.TileMode.CLAMP)
        val wob = sin(SystemClock.uptimeMillis() / 60f) * 0.15f
        rect.set(x - 0.9f + wob, 62f - GROUND, x + 0.9f - wob, -1f)
        c.drawRoundRect(rect, 0.8f, 0.8f, fill)
        fill.shader = null
    }

    // ---------------- Figür ----------------

    /** Kalın, yuvarlak uçlu bir uzuv parçası: önce koyu kenar, sonra renk. */
    private fun limb(c: Canvas, pts: List<Pt>, w: Float, color: Int, outline: Boolean = true) {
        if (outline) {
            stroke.color = OUTLINE
            stroke.strokeWidth = w + 1.3f
            for (i in 0 until pts.size - 1) c.drawLine(pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y, stroke)
        }
        stroke.color = color
        stroke.strokeWidth = w
        for (i in 0 until pts.size - 1) c.drawLine(pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y, stroke)
    }

    private fun hand(c: Canvas, p: Pt, color: Int) {
        fill.color = OUTLINE
        c.drawCircle(p.x, p.y, 3f, fill)
        fill.color = color
        c.drawCircle(p.x, p.y, 2.35f, fill)
    }

    private fun lerpPt(a: Pt, b: Pt, f: Float) = Pt(a.x + (b.x - a.x) * f, a.y + (b.y - a.y) * f)

    private fun drawFigure(c: Canvas, sk: Skeleton, now: Long) {
        // Gölge
        fill.color = 0x33000000
        rect.set(min(sk.nToe.x, sk.fToe.x) - 8f, -1.4f, max(sk.head.x, max(sk.nToe.x, sk.hip.x)) + 8f, 1.4f)
        c.drawOval(rect, fill)

        // 1) Uzaktaki (sağ) bacak ve kol: biraz koyu
        limb(c, listOf(sk.hip, sk.fKnee, sk.fAnkle), 7f, pantsDark)
        limb(c, listOf(sk.fAnkle, sk.fToe), 4.4f, SKIN_DARK)
        limb(c, listOf(sk.shoulder, sk.fElbow, sk.fHand), 5.6f, clothDark)
        hand(c, sk.fHand, SKIN_DARK)

        // 2) Yakındaki bacak
        limb(c, listOf(sk.hip, sk.nKnee, sk.nAnkle), 7.4f, pants)
        limb(c, listOf(sk.nAnkle, sk.nToe), 4.6f, SKIN)

        // 3) Gövde ve cübbe / elbise (kalçadan dize doğru uzanır)
        val hem = lerpPt(sk.hip, sk.nKnee, if (girl) 1.15f else 0.6f)
        val hemFar = lerpPt(sk.hip, sk.fKnee, if (girl) 1.15f else 0.6f)
        stroke.color = OUTLINE
        stroke.strokeWidth = 15.5f
        c.drawLine(sk.hip.x, sk.hip.y, sk.shoulder.x, sk.shoulder.y, stroke)
        stroke.strokeWidth = 12.5f
        c.drawLine(sk.hip.x, sk.hip.y, hem.x, hem.y, stroke)
        c.drawLine(sk.hip.x, sk.hip.y, hemFar.x, hemFar.y, stroke)
        stroke.color = cloth
        stroke.strokeWidth = 14.2f
        c.drawLine(sk.hip.x, sk.hip.y, sk.shoulder.x, sk.shoulder.y, stroke)
        stroke.strokeWidth = 11.2f
        c.drawLine(sk.hip.x, sk.hip.y, hem.x, hem.y, stroke)
        c.drawLine(sk.hip.x, sk.hip.y, hemFar.x, hemFar.y, stroke)
        // Cübbede yaka çizgisi / elbisede kemer süsü
        val mid = lerpPt(sk.hip, sk.shoulder, 0.42f)
        if (girl) {
            fill.color = 0xFFE8C15A.toInt()
            c.drawCircle(mid.x, mid.y, 1.2f, fill)
        } else {
            stroke.color = 0x33000000
            stroke.strokeWidth = 0.7f
            val top = lerpPt(sk.hip, sk.shoulder, 0.95f)
            c.drawLine(mid.x, mid.y, top.x, top.y, stroke)
        }

        // 4) Baş (boyun dahil)
        drawHead(c, sk, now)

        // 5) Yakındaki (sol) kol en önde
        limb(c, listOf(sk.shoulder, sk.nElbow, sk.nHand), 6f, cloth)
        hand(c, sk.nHand, SKIN)
    }

    private fun drawHead(c: Canvas, sk: Skeleton, now: Long) {
        val r = Skeleton.HEAD_R
        var ang = currentPose.t + currentPose.h
        // Yanlış cevapta kısa bir baş sallama
        val since = now - shakeStart
        if (since in 0L..700L) ang += sin(since / 700f * 6f * Math.PI.toFloat()) * 7f * (1f - since / 700f)
        val a = Math.toRadians(ang.toDouble())
        // Baş yönü: F = ileri (yüzün baktığı yön), U = yukarı (başın tepesi)
        val fx = cos(a).toFloat()
        val fy = sin(a).toFloat()
        val ux = sin(a).toFloat()
        val uy = -cos(a).toFloat()
        val hx = sk.head.x
        val hy = sk.head.y
        fun px(f: Float, u: Float) = hx + fx * f + ux * u
        fun py(f: Float, u: Float) = hy + fy * f + uy * u
        val headDeg = ang - 90f // Canvas açısı: başın tepesi yönü

        // Boyun
        val neckTop = Pt(px(-1f, -r * 0.6f), py(-1f, -r * 0.6f))
        limb(c, listOf(sk.shoulder, neckTop), 4.2f, SKIN)

        if (girl) {
            // Başörtüsü: başın arkasına ve omuza dökülen kısım
            fill.color = OUTLINE
            c.drawCircle(px(-0.6f, 0.2f), py(-0.6f, 0.2f), r * 1.2f + 0.65f, fill)
            path.reset()
            path.moveTo(px(-r * 1.05f, 0f), py(-r * 1.05f, 0f))
            path.quadTo(px(-r * 1.3f, -r * 1.3f), py(-r * 1.3f, -r * 1.3f), sk.shoulder.x - fx * 4f, sk.shoulder.y - fy * 4f)
            path.lineTo(sk.shoulder.x + fx * 4f, sk.shoulder.y + fy * 4f)
            path.lineTo(px(r * 0.3f, -r * 0.9f), py(r * 0.3f, -r * 0.9f))
            path.close()
            fill.color = 0xFFE58FB0.toInt()
            c.drawPath(path, fill)
            c.drawCircle(px(-0.6f, 0.2f), py(-0.6f, 0.2f), r * 1.2f, fill)
            // Yüzün göründüğü açıklık
            fill.color = SKIN
            c.drawCircle(px(2.4f, -0.4f), py(2.4f, -0.4f), r * 0.8f, fill)
        } else {
            // Kafa, arkada saç, üstte takke
            fill.color = OUTLINE
            c.drawCircle(hx, hy, r + 0.65f, fill)
            fill.color = SKIN
            c.drawCircle(hx, hy, r, fill)
            fill.color = 0xFF4A3426.toInt()
            rect.set(hx - r, hy - r, hx + r, hy + r)
            c.drawArc(rect, headDeg - 175f, 95f, true, fill) // ense tarafında saç
            fill.color = SKIN
            c.drawCircle(px(0.9f, -0.2f), py(0.9f, -0.2f), r * 0.82f, fill)
            // Takke
            fill.color = OUTLINE
            rect.set(hx - r - 0.7f, hy - r - 0.7f, hx + r + 0.7f, hy + r + 0.7f)
            c.drawArc(rect, headDeg - 82f, 164f, false, fill)
            fill.color = 0xFFFFFFFF.toInt()
            rect.set(hx - r - 0.1f, hy - r - 0.1f, hx + r + 0.1f, hy + r + 0.1f)
            c.drawArc(rect, headDeg - 78f, 156f, false, fill)
            stroke.color = 0x22000000
            stroke.strokeWidth = 0.45f
            c.drawLine(px(-r * 0.75f, r * 0.45f), py(-r * 0.75f, r * 0.45f), px(r * 0.75f, r * 0.45f), py(r * 0.75f, r * 0.45f), stroke)
        }

        // Burun (yandan küçük bir yumru)
        fill.color = OUTLINE
        c.drawCircle(px(r * 0.93f, -0.6f), py(r * 0.93f, -0.6f), 1.55f, fill)
        fill.color = SKIN
        c.drawCircle(px(r * 0.88f, -0.6f), py(r * 0.88f, -0.6f), 1.25f, fill)
        if (girl) {
            fill.color = SKIN
            c.drawCircle(px(r * 0.7f, -0.4f), py(r * 0.7f, -0.4f), 1.7f, fill)
        }

        // Yanak allığı
        fill.color = 0x55FF7A8A
        c.drawCircle(px(3.4f, -2.8f), py(3.4f, -2.8f), 1.7f, fill)

        // Göz: normalde iri ve parlak; arada kırpar; mutluyken "^", yanlışta şaşkın
        if (nextBlink == 0L) nextBlink = now + 2500
        val blinking = now in nextBlink..(nextBlink + 130)
        if (now > nextBlink + 130) nextBlink = now + 2500 + rnd.nextLong(2500)
        val ex = px(4.2f, 1.3f)
        val ey = py(4.2f, 1.3f)
        when {
            face == Face.HAPPY -> {
                stroke.color = EYE
                stroke.strokeWidth = 0.75f
                rect.set(ex - 1.7f, ey - 1.0f, ex + 1.7f, ey + 2.4f)
                c.drawArc(rect, headDeg - 70f, 140f, false, stroke) // tepesi yukarı bakan yay: "^"
            }
            blinking -> {
                stroke.color = EYE
                stroke.strokeWidth = 0.7f
                c.drawLine(px(3f, 1.1f), py(3f, 1.1f), px(5.4f, 1.1f), py(5.4f, 1.1f), stroke)
            }
            else -> {
                fill.color = 0xFFFFFFFF.toInt()
                c.drawCircle(ex, ey, 1.9f, fill)
                fill.color = EYE
                val big = if (face == Face.OOPS) 1.25f else 1.1f
                c.drawCircle(px(4.7f, 1.2f), py(4.7f, 1.2f), big, fill)
                fill.color = 0xFFFFFFFF.toInt()
                c.drawCircle(px(5.0f, 1.65f), py(5.0f, 1.65f), 0.38f, fill)
            }
        }
        // Kaş
        stroke.color = 0x99332218.toInt()
        stroke.strokeWidth = 0.55f
        val browLift = if (face == Face.OOPS) 0.7f else 0f
        c.drawLine(px(3f, 3.9f + browLift), py(3f, 3.9f + browLift), px(5.6f, 4.1f), py(5.6f, 4.1f), stroke)

        // Ağız
        val mx = px(5.6f, -3.4f)
        val my = py(5.6f, -3.4f)
        when (face) {
            Face.OOPS -> {
                fill.color = 0xFF7A2E2E.toInt()
                c.drawCircle(mx, my, 0.85f, fill)
            }
            Face.HAPPY -> {
                fill.color = 0xFF7A2E2E.toInt()
                rect.set(mx - 2f, my - 1.6f, mx + 1.6f, my + 1.6f)
                c.drawArc(rect, ang + 5f, 170f, true, fill) // aşağı doğru açık, gülen ağız
            }
            Face.NORMAL -> {
                stroke.color = 0xFF7A2E2E.toInt()
                stroke.strokeWidth = 0.65f
                rect.set(mx - 1.8f, my - 1.5f, mx + 1.4f, my + 1.2f)
                c.drawArc(rect, ang + 20f, 120f, false, stroke) // tebessüm
            }
        }
    }

    // ---------------- Parçacıklar ve balon ----------------

    private fun drawParticles(c: Canvas) {
        for (p in particles) {
            if (p.life < 0f) continue
            val fade = (1f - p.life / p.maxLife).coerceIn(0f, 1f)
            when (p.kind) {
                0 -> {
                    fill.color = p.color
                    fill.alpha = (200 * fade).toInt()
                    c.drawCircle(p.x, p.y, p.size, fill)
                }
                1 -> {
                    val a = (255 * min(1f, fade * 1.6f)).toInt()
                    val pulse = 0.6f + 0.4f * sin(p.life * 12f)
                    drawStar(c, p.x, p.y, p.size * 0.5f * pulse, (p.color and 0x00FFFFFF) or (a shl 24))
                }
                2 -> {
                    c.save()
                    c.rotate(p.rot, p.x, p.y)
                    fill.color = p.color
                    fill.alpha = (255 * min(1f, fade * 2f)).toInt()
                    rect.set(p.x - p.size, p.y - p.size * 0.5f, p.x + p.size, p.y + p.size * 0.5f)
                    c.drawRect(rect, fill)
                    c.restore()
                }
            }
        }
        fill.alpha = 255
    }

    /** Figürün söylediği söz: başın üstünde, kenarları yuvarlak bir konuşma balonu. */
    private fun drawCaption(c: Canvas, s: String, headX: Float, headTop: Float) {
        text.textSize = 6f
        text.color = 0xFF1E2A26.toInt()
        text.isFakeBoldText = true
        var tw = text.measureText(s)
        if (tw > W - 16f) {
            text.textSize = 6f * (W - 16f) / tw
            tw = text.measureText(s)
        }
        val bw = tw + 8f
        val bh = text.textSize + 5f
        val cx = headX.coerceIn(bw / 2 + 3f, W - bw / 2 - 3f)
        val bottom = max(bh + 3f, min(headTop - 5f, 30f))
        rect.set(cx - bw / 2, bottom - bh, cx + bw / 2, bottom)
        fill.color = 0x40000000
        c.drawRoundRect(RectF(rect.left + 0.6f, rect.top + 0.8f, rect.right + 0.6f, rect.bottom + 0.8f), 4f, 4f, fill)
        fill.color = 0xFFFFFBEF.toInt()
        c.drawRoundRect(rect, 4f, 4f, fill)
        // Balonun ucu başa doğru
        val tipX = headX.coerceIn(rect.left + 4f, rect.right - 4f)
        path.reset()
        path.moveTo(tipX - 2.5f, bottom - 0.2f)
        path.lineTo(tipX + 2.5f, bottom - 0.2f)
        path.lineTo(tipX, min(bottom + 4f, headTop - 0.5f).coerceAtLeast(bottom + 1.5f))
        path.close()
        c.drawPath(path, fill)
        c.drawText(s, cx, bottom - bh / 2 + text.textSize * 0.36f, text)
        text.isFakeBoldText = false
    }
}
