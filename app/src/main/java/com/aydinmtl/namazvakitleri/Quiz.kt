package com.aydinmtl.namazvakitleri

import android.content.Context
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bir soru. [options] içinde ilk şık her zaman doğru cevaptır; ekranda karıştırılır.
 * [source]: isteğe bağlı kaynak (ayet ya da hadis numarası, ör. "Müslim, Zekât 986").
 * Cevaptan sonra açıklamanın altında gösterilir.
 */
data class Question(
    val id: String,
    val level: String,
    val text: String,
    val options: List<String>,
    val info: String,
    val source: String = "",
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
        val out = parse(json)
        cache = out
        return out
    }

    /** sorular.json içeriğini okur. Android'e bağlı değil; testlerde doğrudan kullanılır. */
    fun parse(json: String): List<Question> {
        val arr = JSONObject(json).getJSONArray("questions")
        val out = ArrayList<Question>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val opts = o.getJSONArray("options")
            out.add(
                Question(
                    o.getString("id"), o.getString("level"), o.getString("q"),
                    (0 until opts.length()).map { opts.getString(it) }, o.optString("info", ""),
                    o.optString("source", ""),
                ),
            )
        }
        return out
    }

    fun byLevel(ctx: Context, level: QuizLevel): List<Question> = all(ctx).filter { it.level == level.id }

    fun byId(ctx: Context, id: String): Question? = all(ctx).firstOrNull { it.id == id }
}

/** Bir turda kaç soru sorulur. Seviyedeki bütün sorular turlara bölünür. */
const val ROUND_SIZE = 10

/**
 * Yarım kalan yarışma turu.
 * [order]: bu turun soruları (karışık sırada), [index]: kaçıncı soruda olunduğu, [score]: toplam doğru.
 * [players]: sırayla oynayan aile üyeleri (boşsa tek kişi oynuyor), [scores]: her oyuncunun doğru sayısı.
 * [review]: true ise bu tur "yanlışlarımı tekrar et" turudur.
 */
data class QuizSession(
    val level: QuizLevel,
    val order: List<String>,
    val index: Int,
    val score: Int,
    val players: List<String> = emptyList(),
    val scores: List<Int> = List(players.size) { 0 },
    val review: Boolean = false,
) {
    val finished: Boolean get() = index >= order.size

    /** Şu anki soruyu cevaplayacak oyuncunun sırası (tek kişiyse null). Oyuncular sırayla döner. */
    val currentPlayer: Int? get() = if (players.isEmpty()) null else index % players.size

    /** Şu anki soru cevaplanınca oturumun yeni hali: sıradaki soruya geçilir, doğruysa puan eklenir. */
    fun answered(correct: Boolean): QuizSession {
        val add = if (correct) 1 else 0
        val p = currentPlayer
        val newScores = if (p == null) scores else scores.mapIndexed { i, sc -> if (i == p) sc + add else sc }
        return copy(index = index + 1, score = score + add, scores = newScores)
    }

    fun toJson(): String = JSONObject()
        .put("level", level.id)
        .put("order", JSONArray(order))
        .put("index", index)
        .put("score", score)
        .put("players", JSONArray(players))
        .put("scores", JSONArray(scores))
        .put("review", review)
        .toString()

    companion object {
        fun fromJson(s: String?): QuizSession? = if (s == null) null else try {
            val o = JSONObject(s)
            val arr = o.getJSONArray("order")
            // Eski sürümün kayıtlarında oyuncu bilgisi yok: tek kişi sayılır.
            val pl = o.optJSONArray("players")
            val players = if (pl == null) emptyList() else (0 until pl.length()).map { pl.getString(it) }
            val sc = o.optJSONArray("scores")
            val scores = if (sc != null && sc.length() == players.size) {
                (0 until sc.length()).map { sc.getInt(it) }
            } else {
                List(players.size) { 0 }
            }
            QuizSession(
                QuizLevel.byId(o.getString("level")),
                (0 until arr.length()).map { arr.getString(it) },
                o.getInt("index"), o.getInt("score"),
                players, scores, o.optBoolean("review", false),
            )
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Turların sorularını seçer. Bir seviyedeki bütün sorular görülmeden aynı soru tekrar gelmez;
 * hepsi görülünce deste baştan karıştırılır.
 */
object QuizDeck {
    /**
     * [allIds]: seviyedeki bütün soruların kimlikleri. [seen]: bu destede daha önce sorulanlar.
     * Döner: (bu turun soruları, turdan sonra "görülenler" kümesi).
     */
    fun nextRound(
        allIds: List<String>,
        seen: Set<String>,
        size: Int = ROUND_SIZE,
        random: Random = Random.Default,
    ): Pair<List<String>, Set<String>> {
        val stillValid = seen.intersect(allIds.toSet()) // bankadan çıkarılan soruları unut
        val round = ArrayList(allIds.filter { it !in stillValid }.shuffled(random).take(size))
        if (round.size == size) return Pair(round, stillValid + round)
        // Deste bitti: eksik kalan yeri yeni karışık desteden doldur (bu turdakiler hariç).
        val refill = allIds.filter { it !in round }.shuffled(random).take(size - round.size)
        round.addAll(refill)
        return Pair(round, refill.toSet())
    }

    /**
     * Günün sorusu: aynı gün herkes (ve gün boyu her açılışta) aynı soruyu görür.
     * Sorular her yıl için sabit bir karışık sıraya dizilir; yılın kaçıncı günüyse o soru seçilir.
     * Böylece arka arkaya günlerde aynı konudan soru gelmez ve bir yıl içinde tekrar az olur.
     */
    fun dailyId(allIds: List<String>, date: java.time.LocalDate): String? {
        if (allIds.isEmpty()) return null
        val order = allIds.sorted().shuffled(Random(date.year))
        return order[(date.dayOfYear - 1) % order.size]
    }

    /** Oyuncu adlarını "Annem, Babam ,Zeynep" gibi bir yazıdan ayırır (en çok 8 kişi). */
    fun parsePlayers(text: String): List<String> =
        text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)
}

/**
 * Aile ağacı: her soru İLK KEZ doğru bilindiğinde 1 puan alır. Aynı soruyu tekrar bilmek ağacı
 * büyütmez; böylece ağaç ezberle değil, öğrenilen yeni bilgiyle büyür (120 soru = en çok 120 puan).
 */
object FamilyTree {
    data class Stage(val name: String, val minPoints: Int)

    val STAGES = listOf(
        Stage("Tohum", 0),
        Stage("Filiz", 5),
        Stage("Fidan", 15),
        Stage("Genç ağaç", 30),
        Stage("Çiçekli ağaç", 50),
        Stage("Meyveli ağaç", 75),
        Stage("Ulu çınar", 100),
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

/**
 * "Bu soruda hata var" işaretleri: soru kimliği -> kullanıcının notu (boş olabilir).
 * Cihazda saklanır; istenirse liste olarak paylaşılır (sunucu yok).
 */
object QuizFlags {
    fun fromJson(s: String?): Map<String, String> = try {
        val o = JSONObject(s ?: "{}")
        o.keys().asSequence().associateWith { o.optString(it, "") }
    } catch (e: Exception) {
        emptyMap()
    }

    fun toJson(flags: Map<String, String>): String = JSONObject(flags).toString()

    /** Paylaşılacak metin: her işaretli soru için kimlik, soru, verilen cevap ve not. */
    fun report(flags: Map<String, String>, bank: List<Question>): String {
        val sb = StringBuilder("Aile Halkası: hatalı olabilecek sorular\n")
        for ((id, note) in flags.toSortedMap()) {
            val q = bank.firstOrNull { it.id == id }
            sb.append("\n[").append(id).append("] ")
            if (q == null) {
                sb.append("(soru artık bankada yok)")
            } else {
                sb.append(q.text).append("\nCevap: ").append(q.correct)
            }
            if (note.isNotBlank()) sb.append("\nNot: ").append(note.trim())
            sb.append("\n")
        }
        return sb.toString()
    }
}

/** Sesli okuma (telefonun kendi Türkçe sesi) için metni hazırlar. */
object QuizSpeech {
    /** ✓, ✗, 🌱, ⚑ gibi işaretleri atar; ses motoru bunları "onay işareti" diye okumasın. */
    fun clean(text: String): String =
        text.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\s+]"), "")
            .replace(Regex("[ \\t]+"), " ")
            .lines().joinToString("\n") { it.trim() }
            .trim()

    /** Soru ve şıklar: "Soru? Birinci şık: Mekke. İkinci şık: Medine. ..." */
    fun question(text: String, options: List<String>): String {
        val names = listOf("Birinci", "İkinci", "Üçüncü", "Dördüncü", "Beşinci", "Altıncı")
        val sb = StringBuilder(clean(text))
        options.forEachIndexed { i, o ->
            sb.append("\n").append(names.getOrElse(i) { "${i + 1}." }).append(" şık: ").append(clean(o)).append(".")
        }
        return sb.toString()
    }
}

/** Tur sonucu ekranı: kaç yıldız verileceği ve ona göre simge/başlık. */
object QuizResult {
    /** 0-3 yıldız: %90 ve üstü 3, %60 ve üstü 2, %30 ve üstü 1, altı 0. */
    fun stars(score: Int, total: Int): Int {
        if (total <= 0) return 0
        val pct = score * 100 / total
        return when {
            pct >= 90 -> 3
            pct >= 60 -> 2
            pct >= 30 -> 1
            else -> 0
        }
    }

    /** Yıldız sayısına göre (0..3) büyük simge ve başlık. */
    val EMOJI = listOf("📖", "👍", "🌟", "🏆")
    val TITLE = listOf("Birlikte tekrar edelim", "Güzel başlangıç!", "Çok güzel!", "Maşallah!")
}
