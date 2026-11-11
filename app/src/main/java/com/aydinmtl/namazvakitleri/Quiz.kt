package com.aydinmtl.namazvakitleri

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Bir soru. [options] içinde ilk şık her zaman doğru cevaptır; ekranda karıştırılır. */
data class Question(
    val id: String,
    val level: String,
    val text: String,
    val options: List<String>,
    val info: String,
) {
    val correct: String get() = options[0]
}

enum class QuizLevel(val id: String, val title: String, val hint: String) {
    EASY("kolay", "Kolay", "Peygamber kıssaları, temel bilgiler, güzel ahlak"),
    MEDIUM("orta", "Orta", "Siyer, ibadetler, Kur'an bilgisi"),
    HARD("zor", "Zor", "İslam tarihi, bilim tarihi, fıkıh");

    companion object {
        fun byId(id: String?): QuizLevel = values().firstOrNull { it.id == id } ?: EASY
    }
}

/** Uygulamayla gelen soru bankası (assets/sorular.json). */
object QuizBank {
    private var cache: List<Question>? = null

    @Synchronized
    fun all(ctx: Context): List<Question> {
        cache?.let { return it }
        val json = ctx.assets.open("sorular.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JSONObject(json).getJSONArray("questions")
        val out = ArrayList<Question>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val opts = o.getJSONArray("options")
            out.add(
                Question(
                    o.getString("id"), o.getString("level"), o.getString("q"),
                    (0 until opts.length()).map { opts.getString(it) }, o.optString("info", ""),
                ),
            )
        }
        cache = out
        return out
    }

    fun byLevel(ctx: Context, level: QuizLevel): List<Question> = all(ctx).filter { it.level == level.id }

    fun byId(ctx: Context, id: String): Question? = all(ctx).firstOrNull { it.id == id }
}

/** Yarım kalan yarışma: seviye, karışık soru sırası, kaçıncı soruda olunduğu ve doğru sayısı. */
data class QuizSession(val level: QuizLevel, val order: List<String>, val index: Int, val score: Int) {
    val finished: Boolean get() = index >= order.size

    fun toJson(): String = JSONObject()
        .put("level", level.id)
        .put("order", JSONArray(order))
        .put("index", index)
        .put("score", score)
        .toString()

    companion object {
        /** Her yeni yarışmada sorular farklı sırayla gelir. */
        fun newGame(ctx: Context, level: QuizLevel): QuizSession =
            QuizSession(level, QuizBank.byLevel(ctx, level).map { it.id }.shuffled(), 0, 0)

        fun fromJson(s: String?): QuizSession? = if (s == null) null else try {
            val o = JSONObject(s)
            val arr = o.getJSONArray("order")
            QuizSession(
                QuizLevel.byId(o.getString("level")),
                (0 until arr.length()).map { arr.getString(it) },
                o.getInt("index"), o.getInt("score"),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/** Aile ağacı: bütün yarışmalardaki toplam doğru cevapla büyür. */
object FamilyTree {
    data class Stage(val name: String, val minPoints: Int)

    val STAGES = listOf(
        Stage("Tohum", 0),
        Stage("Filiz", 10),
        Stage("Fidan", 25),
        Stage("Genç ağaç", 50),
        Stage("Çiçekli ağaç", 100),
        Stage("Meyveli ağaç", 175),
        Stage("Ulu çınar", 275),
    )

    fun stageIndex(points: Int): Int = STAGES.indexOfLast { points >= it.minPoints }.coerceAtLeast(0)

    /** Sonraki aşamaya kaç doğru kaldığı (son aşamadaysa null). */
    fun toNext(points: Int): Int? {
        val i = stageIndex(points)
        return if (i >= STAGES.size - 1) null else STAGES[i + 1].minPoints - points
    }
}

/** Ailenin gerçek hayat hedefi: "Ağacımız Meyveli ağaç olunca birlikte dondurma yemeye gidiyoruz". */
data class FamilyGoal(val text: String, val stage: Int) {
    fun toJson(): JSONObject = JSONObject().put("text", text).put("stage", stage)

    companion object {
        fun listFromJson(s: String?): List<FamilyGoal> = try {
            val arr = JSONArray(s ?: "[]")
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                FamilyGoal(o.getString("text"), o.getInt("stage"))
            }
        } catch (e: Exception) {
            emptyList()
        }

        fun listToJson(list: List<FamilyGoal>): String = JSONArray(list.map { it.toJson() }).toString()
    }
}
