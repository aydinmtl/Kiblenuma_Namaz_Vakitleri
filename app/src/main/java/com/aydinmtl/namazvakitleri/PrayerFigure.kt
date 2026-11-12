package com.aydinmtl.namazvakitleri

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Namaz oyunundaki çocuk figürünün "iskeleti". Android'e bağlı değil; testlerde doğrudan kullanılır.
 *
 * Figür yandan görünür ve sağa (kıbleye) bakar. Bir duruş ([Pose]) yalnızca açılardan oluşur:
 * - t: gövdenin dikten öne eğilmesi (0 dik, 90 rükûdaki gibi yatay).
 * - h: başın gövdeye göre ek eğimi.
 * - Kol, bacak ve ayak açıları "aşağıdan ölçülür, + öne doğru" kuralıyla verilir:
 *   0 = dümdüz aşağı, 90 = öne yatay, 180 = yukarı, -90 = arkaya yatay.
 * - n... = yakındaki (bize dönük, sol) kol/bacak; f... = uzaktaki (sağ) kol/bacak.
 *
 * İki duruş arasında açılar yumuşakça değiştirilerek ([lerp]) canlandırma yapılır.
 * Figür her an "yere basacak" şekilde aşağı yukarı kaydırılır ([Skeleton.of]); böylece
 * oturma, secde gibi duruşlarda da havada kalmaz.
 */
data class Pose(
    val t: Float, val h: Float,
    val na1: Float, val na2: Float, val fa1: Float, val fa2: Float,
    val nl1: Float, val nl2: Float, val nft: Float,
    val fl1: Float, val fl2: Float, val fft: Float,
) {
    private fun toArray() = floatArrayOf(t, h, na1, na2, fa1, fa2, nl1, nl2, nft, fl1, fl2, fft)

    companion object {
        private fun fromArray(a: FloatArray) = Pose(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11])

        /**
         * İki duruşun arası. [f] 0 ise [a], 1 ise [b]. Açılar en kısa yoldan döner
         * (ör. 170°'den -170°'ye giderken kol tam tur atmaz, 20° döner).
         */
        fun lerp(a: Pose, b: Pose, f: Float): Pose {
            val x = a.toArray()
            val y = b.toArray()
            return fromArray(FloatArray(x.size) { i -> x[i] + shortest(x[i], y[i]) * f })
        }

        /** [from] açısından [to] açısına en kısa dönüş (-180..180). */
        fun shortest(from: Float, to: Float): Float {
            var d = (to - from) % 360f
            if (d > 180f) d -= 360f
            if (d < -180f) d += 360f
            return d
        }
    }
}

/** Ekrandaki bir nokta (figür birimiyle). */
data class Pt(val x: Float, val y: Float)

/**
 * Bir duruşun eklem noktaları. Birim: figürün boyu yaklaşık 100 birim. y aşağı doğru artar.
 * [of] noktaları, figürün en alttaki noktası y = 0 (yer) olacak şekilde kaydırarak verir.
 */
class Skeleton(
    val hip: Pt, val shoulder: Pt, val head: Pt,
    val nElbow: Pt, val nHand: Pt, val fElbow: Pt, val fHand: Pt,
    val nKnee: Pt, val nAnkle: Pt, val nToe: Pt,
    val fKnee: Pt, val fAnkle: Pt, val fToe: Pt,
) {
    companion object {
        // Uzuvların boyları
        const val TORSO = 30f
        const val NECK = 12f // omuzdan baş merkezine
        const val HEAD_R = 8.5f
        const val UPPER_ARM = 16f
        const val FOREARM = 15f
        const val THIGH = 24f
        const val SHIN = 23f
        const val FOOT = 8f

        private fun rad(d: Float) = Math.toRadians(d.toDouble())

        /** [p] noktasından, [deg] açısıyla (aşağıdan, + öne) [len] uzunlukta gidilen nokta. */
        private fun go(p: Pt, deg: Float, len: Float) =
            Pt(p.x + (sin(rad(deg)) * len).toFloat(), p.y + (cos(rad(deg)) * len).toFloat())

        fun of(p: Pose): Skeleton {
            val hip0 = Pt(0f, 0f)
            // Gövde: kalçadan yukarı, t kadar öne eğik
            val sh = Pt((sin(rad(p.t)) * TORSO).toFloat(), (-cos(rad(p.t)) * TORSO).toFloat())
            val ht = p.t + p.h
            val head = Pt(sh.x + (sin(rad(ht)) * NECK).toFloat(), sh.y - (cos(rad(ht)) * NECK).toFloat())
            val ne = go(sh, p.na1, UPPER_ARM)
            val nh = go(ne, p.na2, FOREARM)
            val fe = go(sh, p.fa1, UPPER_ARM)
            val fh = go(fe, p.fa2, FOREARM)
            val nk = go(hip0, p.nl1, THIGH)
            val na = go(nk, p.nl2, SHIN)
            val nt = go(na, p.nft, FOOT)
            val fk = go(hip0, p.fl1, THIGH)
            val fa = go(fk, p.fl2, SHIN)
            val ft = go(fa, p.fft, FOOT)
            // Yere basma: en alttaki nokta y = 0 olsun
            val all = listOf(hip0, sh, ne, nh, fe, fh, nk, na, nt, fk, fa, ft)
            val low = maxOf(all.maxOf { it.y }, head.y + HEAD_R)
            fun s(q: Pt) = Pt(q.x, q.y - low)
            return Skeleton(s(hip0), s(sh), s(head), s(ne), s(nh), s(fe), s(fh), s(nk), s(na), s(nt), s(fk), s(fa), s(ft))
        }
    }

    /** Bütün noktalar (test için). */
    fun points(): List<Pt> = listOf(hip, shoulder, head, nElbow, nHand, fElbow, fHand, nKnee, nAnkle, nToe, fKnee, fAnkle, fToe)
}

/**
 * Hazır duruşlar. Açılar, ellerin hedef noktaya (ör. dizler, kulak hizası, yer) gelmesi için
 * hesaplanıp buraya yazıldı. "_m" erkek, "_f" kız çocuğu içindir; [get] cinsiyete göre seçer.
 */
object Poses {
    private val map: Map<String, Pose> = run {
        val m = linkedMapOf(
            // ---- Namaz ----
            "stand" to Pose(0f, 0f, -4f, -2f, 4f, 2f, 0f, 0f, 90f, 0f, 0f, 90f),
            // Başlama tekbiri: erkek eller kulak hizasında, kız omuz hizasında (Hanefi tarifi)
            "tekbir_m" to Pose(0f, 0f, 92.8f, -132.9f, 85.6f, -138.4f, 0f, 0f, 90f, 0f, 0f, 90f),
            "tekbir_f" to Pose(0f, 0f, 29.9f, -172.4f, 20.9f, 175.1f, 0f, 0f, 90f, 0f, 0f, 90f),
            // Kıyam: erkek eller göbek altında, kız göğüs üzerinde bağlı (Hanefi tarifi)
            "kiyam_m" to Pose(0f, 0f, -13.6f, 45.8f, -10.5f, 41.3f, 0f, 0f, 90f, 0f, 0f, 90f),
            "kiyam_f" to Pose(0f, 0f, -29.9f, 93.3f, -30f, 89.5f, 0f, 0f, 90f, 0f, 0f, 90f),
            "ruku" to Pose(88f, 0f, -38f, -38f, -34f, -34f, 14f, -3f, 90f, 14f, -3f, 90f),
            // Secdeye inerken/kalkarken ara duruş: dizler yerde, gövde dik
            "kneel" to Pose(0f, 0f, 5f, 5f, 8f, 8f, 0f, -88f, 0f, 0f, -88f, 0f),
            // Secde: alın ve burun yerde, eller kulak hizasında yere düz konmuş, dirsekler yerden
            // kalkık; dizler yerde, ayak parmakları yere basıp kıbleye dönük, kalça yukarıda.
            "secde" to Pose(98f, 37f, -35.8f, 81.6f, -29.8f, 85f, 28.2f, -105f, -35f, 28.2f, -105f, -35f),
            "sit" to Pose(4f, 0f, 18f, 32.1f, 24.3f, 27.1f, 84f, -92f, -90f, 84f, -92f, -90f),
            "selam_r" to Pose(4f, -6f, 18f, 32.1f, 24.3f, 27.1f, 84f, -92f, -90f, 84f, -92f, -90f),
            "selam_l" to Pose(4f, 6f, 18f, 32.1f, 24.3f, 27.1f, 84f, -92f, -90f, 84f, -92f, -90f),
            // ---- Abdest: tabureye oturmuş, musluk önde ----
            "a_sit" to Pose(0f, 0f, 28.3f, 31.1f, 29f, 31.7f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_hands" to Pose(0f, 0f, 54.9f, 57.1f, 54.5f, 56.7f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_mouth" to Pose(0f, 6f, 58.2f, -163.1f, 54.9f, 57.1f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_nose" to Pose(0f, 6f, 79.1f, -158.7f, 54.9f, 57.1f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_face1" to Pose(0f, 6f, 103.8f, -151f, 100.6f, -150.1f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_face2" to Pose(0f, 6f, 65.3f, -155.2f, 60.1f, -158f, 88f, 2f, 90f, 86f, 0f, 90f),
            // Sağ kol (uzaktaki) öne uzanır, sol el onu bilekten dirseğe ovar
            "a_rarm1" to Pose(0f, 0f, 60.5f, 63.2f, 54.9f, 57.1f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_rarm2" to Pose(0f, 0f, 12.4f, 104f, 54.9f, 57.1f, 88f, 2f, 90f, 86f, 0f, 90f),
            // Sol kol (yakındaki) öne uzanır, sağ el onu ovar
            "a_larm1" to Pose(0f, 0f, 54.9f, 57.1f, 60.5f, 63.2f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_larm2" to Pose(0f, 0f, 54.9f, 57.1f, 12.4f, 104f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_head1" to Pose(0f, 0f, 125.6f, -147.7f, 120.7f, -148.8f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_head2" to Pose(0f, 0f, 151f, -113.6f, 146.3f, -112.3f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_ear" to Pose(0f, 0f, 115.6f, -105.8f, 108.5f, -109.1f, 88f, 2f, 90f, 86f, 0f, 90f),
            // Ense meshi: ellerin dışı başın arkasından enseye doğru
            "a_neck" to Pose(0f, -8f, -202.1f, -101.8f, -206.4f, -99.8f, 88f, 2f, 90f, 86f, 0f, 90f),
            "a_neck2" to Pose(0f, -8f, -205.5f, -115.4f, -210.2f, -113.8f, 88f, 2f, 90f, 86f, 0f, 90f),
            // Ayak yıkama: ayak musluğun altına uzatılır, eller ayağı ovar
            "a_rfoot" to Pose(28f, 0f, 29.4f, 31.4f, 30.7f, 32.7f, 88f, 2f, 90f, 80f, 50f, 90f),
            "a_rfoot2" to Pose(34f, 0f, 22.1f, 24.3f, 23.8f, 26f, 88f, 2f, 90f, 80f, 50f, 90f),
            "a_lfoot" to Pose(28f, 0f, 29.4f, 31.4f, 30.7f, 32.7f, 80f, 50f, 90f, 86f, 0f, 90f),
            "a_lfoot2" to Pose(34f, 0f, 22.1f, 24.3f, 23.8f, 26f, 80f, 50f, 90f, 86f, 0f, 90f),
        )
        // Yıkarken ileri geri ovma hareketi için ikinci duruşlar (eller biraz kayar)
        m["a_hands2"] = m.getValue("a_hands").copy(na2 = 70f, fa2 = 44f)
        m["a_mouth2"] = m.getValue("a_mouth").copy(na1 = 52f)
        m["a_nose2"] = m.getValue("a_nose").copy(na1 = 72f)
        m["a_ear2"] = m.getValue("a_ear").copy(na2 = -95f, fa2 = -98f)
        m
    }

    /** Duruşun adı (ör. "tekbir") ve cinsiyete göre duruş. Önce "tekbir_f"/"tekbir_m" aranır. */
    fun get(name: String, girl: Boolean): Pose =
        map[name + if (girl) "_f" else "_m"] ?: map[name] ?: map.getValue("stand")

    fun has(name: String): Boolean = map.containsKey(name) || map.containsKey(name + "_m")

    val names: Set<String> get() = map.keys

    /** İki açının birbirine yakın olup olmadığı (test için). */
    fun close(a: Float, b: Float, eps: Float = 0.5f) = abs(Pose.shortest(a, b)) < eps
}
