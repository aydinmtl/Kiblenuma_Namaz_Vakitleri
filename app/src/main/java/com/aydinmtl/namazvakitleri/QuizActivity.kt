package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import java.time.LocalDate
import java.util.Locale

/**
 * Aile Halkası: bilgi yarışması.
 * - Seviye seçilir (Kolay/Orta/Zor). Yarışma 10 soruluk turlardır; seviyedeki bütün sorular
 *   görülmeden aynı soru tekrar gelmez (QuizDeck).
 * - İstenirse aile üyeleri sırayla oynar ("Sıra: Zeynep"); tur sonunda kişi kişi puan gösterilir.
 * - Cevaptan sonra doğrusu ve kısa açıklama gösterilir, "Sıradaki soru" ile devam edilir.
 * - İstenildiği an kapatılabilir; tekrar girince "kaldığın yerden devam" sorulur.
 * - Bir soru İLK KEZ doğru bilinince aile ağacı büyür; ağaç aşamalarına aile hedefleri bağlanabilir.
 * - Yanlış bilinen sorular "Yanlışlarımı tekrar et" turunda yeniden sorulur.
 * - Her gün bir "günün sorusu" (ana ekrandan ya da akşam bildiriminden açılır).
 * - "🔊 Oku": soru ve şıklar telefonun kendi Türkçe sesiyle okunur (okuma bilmeyen küçükler için).
 */
class QuizActivity : Activity() {

    private lateinit var homePane: View
    private lateinit var playPane: View
    private lateinit var tree: TreeView
    private lateinit var tvTree: TextView
    private lateinit var goalList: LinearLayout
    private lateinit var btnReview: Button
    private lateinit var tvTurn: TextView
    private lateinit var tvProgress: TextView
    private lateinit var bar: ProgressBar
    private lateinit var tvQuestion: TextView
    private lateinit var options: LinearLayout
    private lateinit var tvInfo: TextView
    private lateinit var btnNext: Button
    private lateinit var btnFlag: TextView
    private lateinit var btnFlags: Button
    private var currentQuestion: Question? = null
    private lateinit var btnSpeak: TextView
    private lateinit var cbDaily: CheckBox

    // Sesli okuma: telefonun kendi metin okuma motoru (ek kütüphane yok). İlk kullanımda açılır.
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    companion object {
        /** true ise ekran "günün sorusu" ile açılır (ana ekrandaki düğme ya da bildirim). */
        const val EXTRA_DAILY = "daily"
    }

    private var session: QuizSession? = null
    private var answered = false
    private var askedResume = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz)
        // Geri hareketi / geri tuşu. Android 13+ yeni yolu kullanır (hedef API 36'da
        // eski onBackPressed artık çağrılmaz); eskiler aşağıdaki onBackPressed'i kullanır.
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) { goBack() }
        }
        Ui.fitSystemBars(this, findViewById(R.id.root))

        homePane = findViewById(R.id.homePane)
        playPane = findViewById(R.id.playPane)
        tree = findViewById(R.id.tree)
        tvTree = findViewById(R.id.tvTree)
        goalList = findViewById(R.id.goalList)
        btnReview = findViewById(R.id.btnReview)
        tvTurn = findViewById(R.id.tvTurn)
        tvProgress = findViewById(R.id.tvProgress)
        bar = findViewById(R.id.bar)
        tvQuestion = findViewById(R.id.tvQuestion)
        options = findViewById(R.id.options)
        tvInfo = findViewById(R.id.tvInfo)
        btnNext = findViewById(R.id.btnNext)
        btnFlag = findViewById(R.id.btnFlag)
        btnFlags = findViewById(R.id.btnFlags)
        btnSpeak = findViewById(R.id.btnSpeak)
        cbDaily = findViewById(R.id.cbDaily)

        setupLevelButton(R.id.btnEasy, QuizLevel.EASY)
        setupLevelButton(R.id.btnMedium, QuizLevel.MEDIUM)
        setupLevelButton(R.id.btnHard, QuizLevel.HARD)
        btnReview.setOnClickListener { askPlayersThen { players -> startReview(players) } }
        findViewById<View>(R.id.btnAddGoal).setOnClickListener { addGoal() }
        findViewById<View>(R.id.btnClose).setOnClickListener { showHome() }
        btnNext.setOnClickListener { showQuestion() }
        btnFlag.setOnClickListener { currentQuestion?.let { flagQuestion(it) } }
        btnFlags.setOnClickListener { showFlags() }
        btnSpeak.setOnClickListener { speakCurrent() }
        findViewById<View>(R.id.btnDailyHome).setOnClickListener { showDaily() }
        findViewById<View>(R.id.btnResetTree).setOnClickListener { askResetTree() }
        cbDaily.isChecked = Store.dailyNotify(this)
        cbDaily.setOnCheckedChangeListener { _, on -> Store.setDailyNotify(this, on) }

        showHome()
        if (intent?.getBooleanExtra(EXTRA_DAILY, false) == true) {
            askedResume = true // günün sorusu için açıldıysa "devam et" sorusu araya girmesin
            showDaily()
        }
    }

    // Ekran zaten açıkken bildirime dokunulursa buraya gelir.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_DAILY, false)) showDaily()
    }

    override fun onDestroy() {
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Yarım kalmış yarışma varsa sor (ekran her açıldığında bir kez).
        val s = Store.quizSession(this)
        if (!askedResume && playPane.visibility != View.VISIBLE && s != null && !s.finished) {
            askedResume = true
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Kaldığın yerden devam etmek ister misin?")
                .setMessage("${title(s)} · ${s.index + 1}. soru / ${s.order.size} · ${s.score} doğru")
                .setPositiveButton("Devam et") { _, _ -> resume(s) }
                .setNegativeButton("Yeni tur") { _, _ -> if (s.review) startReview(s.players) else start(s.level, s.players) }
                .setNeutralButton("Vazgeç", null)
                .show()
        }
    }

    // Android 12 ve öncesinde geri tuşu buraya gelir.
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goBack()

    /** Geri: yarışma ekranındaysak ana sayfaya dön (yarışma kayıtlı kalır), değilse ekranı kapat. */
    private fun goBack() {
        if (playPane.visibility == View.VISIBLE) showHome() else finish()
    }

    // ---------------- Ana sayfa ----------------

    private fun setupLevelButton(id: Int, level: QuizLevel) {
        val b = findViewById<Button>(id)
        val count = QuizBank.byLevel(this, level).size
        b.text = "${level.title}\n${level.hint} · $count soru, ${minOf(ROUND_SIZE, count)} soruluk turlar"
        b.setOnClickListener {
            val s = Store.quizSession(this)
            if (s != null && !s.finished) {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Yarım kalan tur var")
                    .setMessage("${title(s)}: ${s.index + 1}. sorudasın. Ne yapalım?")
                    .setPositiveButton("Devam et") { _, _ -> resume(s) }
                    .setNegativeButton("Yeni ${level.title} turu") { _, _ ->
                        askPlayersThen { players -> start(level, players) }
                    }
                    .show()
            } else {
                askPlayersThen { players -> start(level, players) }
            }
        }
    }

    private fun showHome() {
        playPane.visibility = View.GONE
        homePane.visibility = View.VISIBLE
        val points = Store.treePoints(this)
        val stage = FamilyTree.stageIndex(points)
        tree.stage = stage
        tree.invalidate()
        val next = FamilyTree.toNext(points)
        tvTree.text = "Ağacımız: ${FamilyTree.STAGES[stage].name} · toplam $points doğru" +
            if (next != null) "\nSonraki aşamaya (${FamilyTree.STAGES[stage + 1].name}) $next doğru kaldı" else "\nAğacımız en büyük haline ulaştı!"
        renderGoals(stage)

        // Yanlış bilinen soru varsa tekrar düğmesini göster (bankadan çıkmış sorular sayılmaz).
        val wrong = reviewIds().size
        btnReview.visibility = if (wrong > 0) View.VISIBLE else View.GONE
        btnReview.text = "Yanlışlarımı tekrar et ($wrong soru)"

        // "Bu soruda hata var" diye işaretlenmiş soru varsa listesini gösteren düğme
        val flags = Store.quizFlags(this).size
        btnFlags.visibility = if (flags > 0) View.VISIBLE else View.GONE
        btnFlags.text = "İşaretlediğim sorular ($flags)"

        val dailyDone = Store.dailyDoneDate(this) == LocalDate.now().toString()
        findViewById<Button>(R.id.btnDailyHome).text = if (dailyDone) "💡 Günün sorusu ✓ (bugün cevaplandı)" else "💡 Günün sorusu"
    }

    /** Tur başlığı: "Kolay seviye" ya da "Yanlışları tekrar". */
    private fun title(s: QuizSession): String = if (s.review) "Yanlışları tekrar" else "${s.level.title} seviye"

    /** Yanlış listesindeki ve hâlâ soru bankasında olan sorular. */
    private fun reviewIds(): List<String> {
        val all = QuizBank.all(this).map { it.id }.toSet()
        return Store.wrongIds(this).filter { it in all }
    }

    // ---------------- Yarışma ----------------

    /**
     * "Kimler oynuyor?" penceresi. Adlar virgülle ayrılır; boş bırakılırsa tek kişi oynar.
     * Bir önceki yarışmanın adları hazır gelir.
     */
    private fun askPlayersThen(onReady: (List<String>) -> Unit) {
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad / 2, pad, 0)
        val input = EditText(this)
        input.hint = "Ör: Annem, Babam, Zeynep"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        input.setText(Store.quizPlayers(this).joinToString(", "))
        box.addView(input)
        val note = TextView(this)
        note.text = "Adları virgülle ayır; sorular sırayla gelir. Tek kişi oynuyorsan boş bırak."
        note.setPadding(0, pad / 2, 0, 0)
        box.addView(note)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Kimler oynuyor?")
            .setView(box)
            .setPositiveButton("Başla") { _, _ ->
                val players = QuizDeck.parsePlayers(input.text.toString())
                Store.saveQuizPlayers(this, players)
                onReady(players)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    /** Seviyeden yeni 10 soruluk tur: daha önce bu destede sorulmamış sorular önce gelir. */
    private fun start(level: QuizLevel, players: List<String>) {
        val all = QuizBank.byLevel(this, level).map { it.id }
        val (round, seen) = QuizDeck.nextRound(all, Store.seenIds(this, level))
        Store.saveSeenIds(this, level, seen)
        val s = QuizSession(level, round, 0, 0, players)
        Store.saveQuizSession(this, s)
        resume(s)
    }

    /** Yanlış bilinen sorulardan (en çok 10) tekrar turu. */
    private fun startReview(players: List<String>) {
        val ids = reviewIds().shuffled().take(ROUND_SIZE)
        if (ids.isEmpty()) {
            showHome()
            return
        }
        val s = QuizSession(QuizLevel.EASY, ids, 0, 0, players, review = true)
        Store.saveQuizSession(this, s)
        resume(s)
    }

    private fun resume(s: QuizSession) {
        session = s
        homePane.visibility = View.GONE
        playPane.visibility = View.VISIBLE
        showQuestion()
    }

    private fun showQuestion() {
        val s = session ?: return
        if (s.finished) {
            showResult(s)
            return
        }
        val q = QuizBank.byId(this, s.order[s.index])
        if (q == null) { // soru bankası güncellendiyse eksik soruyu atla
            session = s.copy(index = s.index + 1).also { Store.saveQuizSession(this, it) }
            showQuestion()
            return
        }
        answered = false
        currentQuestion = q
        tts?.stop()
        btnFlag.visibility = View.GONE
        showTurn(s)
        tvProgress.text = "${title(s)} · Soru ${s.index + 1} / ${s.order.size} · ✓ ${s.score}"
        bar.max = s.order.size
        bar.progress = s.index
        tvQuestion.text = q.text
        tvInfo.visibility = View.GONE
        btnNext.visibility = View.GONE

        options.removeAllViews()
        // Şıklar her gösterimde karışık sırada
        for (opt in q.options.shuffled()) {
            val b = Button(this)
            b.text = opt
            b.isAllCaps = false
            b.textSize = 17f
            b.setTextColor(getColor(R.color.text))
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setBackgroundResource(R.drawable.opt_normal)
            b.stateListAnimator = null
            val pad = (14 * resources.displayMetrics.density).toInt()
            b.setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (8 * resources.displayMetrics.density).toInt()
            b.layoutParams = lp
            b.tag = opt // asıl şık metni (cevaptan sonra yazının başına ✓ / ✗ eklenir)
            b.setOnClickListener { answer(q, opt) }
            options.addView(b)
        }
        findViewById<View>(R.id.root).scrollTo(0, 0)
    }

    private fun answer(q: Question, chosen: String) {
        if (answered) return
        answered = true
        val s = session ?: return
        val ok = chosen == q.correct
        val oldStage = FamilyTree.stageIndex(Store.treePoints(this))

        // Doğru şıkkı yeşil, yanlış seçileni kırmızı göster. Renk ayırt edemeyenler ve ekran
        // okuyucu (TalkBack) kullananlar için yazının başına da ✓ / ✗ konur.
        for (i in 0 until options.childCount) {
            val b = options.getChildAt(i) as Button
            b.isEnabled = false
            val opt = b.tag as? String ?: b.text.toString()
            when (opt) {
                q.correct -> {
                    b.setBackgroundResource(R.drawable.opt_correct)
                    b.text = "✓  $opt"
                    b.contentDescription = "$opt, doğru cevap"
                }
                chosen -> {
                    b.setBackgroundResource(R.drawable.opt_wrong)
                    b.text = "✗  $opt"
                    b.contentDescription = "$opt, senin cevabın, yanlış"
                }
            }
        }
        tvInfo.text = (if (ok) "✓ Doğru!" else "✗ Doğru cevap: ${q.correct}") +
            (if (q.info.isNotEmpty()) "\n${q.info}" else "") +
            (if (q.source.isNotEmpty()) "\nKaynak: ${q.source}" else "")
        tvInfo.setTextColor(getColor(if (ok) R.color.gold else R.color.text))
        tvInfo.visibility = View.VISIBLE

        // Hemen kaydet: kapatılırsa bir sonraki sorudan devam edilir.
        val updated = s.answered(ok)
        session = updated
        Store.saveQuizSession(this, if (updated.finished) null else updated)
        // Ağaç yalnızca ilk kez doğru bilinen soruyla büyür. Yanlışlar tekrar listesine girer,
        // doğru bilinince listeden çıkar.
        val firstTime = ok && Store.markSolved(this, q.id)
        Store.setWrong(this, q.id, !ok)
        if (ok) {
            tvInfo.text = tvInfo.text.toString() +
                if (firstTime) "\n🌱 Ağacımıza +1" else "\n(Bu soruyu daha önce bilmiştiniz; ağaç yalnızca yeni bilgiyle büyür.)"
        }

        btnNext.text = if (updated.finished) "Sonucu gör ›" else "Sıradaki soru ›"
        btnNext.visibility = View.VISIBLE
        updateFlagButton(q)
        tvProgress.text = "${title(s)} · Soru ${s.index + 1} / ${s.order.size} · ✓ ${updated.score}"
        bar.progress = updated.index

        val newStage = FamilyTree.stageIndex(Store.treePoints(this))
        if (newStage > oldStage) celebrate(newStage)
    }

    // ---------------- Sesli okuma ----------------

    /** Cevaptan önce soru ve şıkları, cevaptan sonra sonucu ve açıklamayı okur. */
    private fun speakCurrent() {
        val q = currentQuestion ?: return
        val text = if (answered) {
            QuizSpeech.clean(tvInfo.text.toString())
        } else {
            val shown = (0 until options.childCount).map { (options.getChildAt(it).tag as? String).orEmpty() }
            QuizSpeech.question(q.text, shown)
        }
        speak(text)
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "quiz")
            return
        }
        pendingSpeech = text
        if (engine != null) return // motor hâlâ açılıyor; hazır olunca okunur
        tts = TextToSpeech(this) init@{ status ->
            val t = tts ?: return@init
            val lang = if (status == TextToSpeech.SUCCESS) t.setLanguage(Locale.forLanguageTag("tr-TR")) else TextToSpeech.LANG_NOT_SUPPORTED
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(
                    this,
                    "Telefonda Türkçe ses bulunamadı. Ayarlar → Erişilebilirlik → Metin okuma'dan Türkçe sesi indirebilirsin.",
                    Toast.LENGTH_LONG,
                ).show()
                t.shutdown()
                tts = null
                return@init
            }
            ttsReady = true
            pendingSpeech?.let { t.speak(it, TextToSpeech.QUEUE_FLUSH, null, "quiz") }
            pendingSpeech = null
        }
    }

    // ---------------- Günün sorusu ----------------

    /** Bugünün sorusunu açar. Cevap verilince sonuç ve açıklama gösterilir; ilk kez bilinirse ağaç büyür. */
    private fun showDaily() {
        val today = LocalDate.now()
        val id = QuizDeck.dailyId(QuizBank.all(this).map { it.id }, today) ?: return
        val q = QuizBank.byId(this, id) ?: return
        val opts = q.options.shuffled()
        val dp = resources.displayMetrics.density

        // Soru metni ve şıklar tek bir pencerede
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding((20 * dp).toInt(), (8 * dp).toInt(), (20 * dp).toInt(), 0)
        val tvQ = TextView(this)
        tvQ.text = q.text
        tvQ.textSize = 18f
        tvQ.setTextColor(getColor(R.color.text))
        box.addView(tvQ)

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("💡 Günün sorusu")
            .setView(box)
            .setNegativeButton("Kapat", null)
            .setNeutralButton("🔊 Oku", null) // tıklanınca pencere kapanmasın diye aşağıda bağlanıyor
            .create()
        for (opt in opts) {
            val b = Button(this)
            b.text = opt
            b.isAllCaps = false
            b.textSize = 16f
            b.setTextColor(getColor(R.color.text))
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setBackgroundResource(R.drawable.opt_normal)
            b.stateListAnimator = null
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (8 * dp).toInt()
            b.layoutParams = lp
            b.setOnClickListener {
                dialog.dismiss()
                answerDaily(q, opt, today)
            }
            box.addView(b)
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { speak(QuizSpeech.question(q.text, opts)) }
        }
        dialog.setOnDismissListener { tts?.stop() }
        dialog.show()
    }

    private fun answerDaily(q: Question, chosen: String, today: LocalDate) {
        val ok = chosen == q.correct
        val oldStage = FamilyTree.stageIndex(Store.treePoints(this))
        val firstTime = ok && Store.markSolved(this, q.id)
        Store.setWrong(this, q.id, !ok)
        Store.setDailyDone(this, today)
        val msg = StringBuilder(if (ok) "✓ Doğru!" else "✗ Doğru cevap: ${q.correct}")
        if (q.info.isNotEmpty()) msg.append("\n\n").append(q.info)
        if (q.source.isNotEmpty()) msg.append("\nKaynak: ").append(q.source)
        if (firstTime) msg.append("\n\n🌱 Ağacımıza +1")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Günün sorusu")
            .setMessage(msg.toString())
            .setPositiveButton("Tamam") { _, _ ->
                val newStage = FamilyTree.stageIndex(Store.treePoints(this))
                if (newStage > oldStage) celebrate(newStage)
            }
            .show()
        showHome()
    }

    // ---------------- Ağacı sıfırla ----------------

    private fun askResetTree() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Ağaç sıfırlansın mı?")
            .setMessage(
                "Ağacımız tohuma döner ve bütün sorular ağacı yeniden büyütebilir. " +
                    "Hedefler ve yanlışlar listesi silinmez. Bu işlem geri alınamaz.",
            )
            .setPositiveButton("Sıfırla") { _, _ ->
                Store.resetTree(this)
                showHome()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    // ---------------- "Bu soruda hata var" ----------------

    private fun updateFlagButton(q: Question) {
        btnFlag.text = if (q.id in Store.quizFlags(this)) "⚑ İşaretlendi (kaldırmak için dokun)" else "⚑ Bu soruda hata var"
        btnFlag.visibility = View.VISIBLE
    }

    /** Soruyu işaretle (isteğe bağlı not ile) ya da işareti kaldır. Hiçbir yere gönderilmez. */
    private fun flagQuestion(q: Question) {
        if (q.id in Store.quizFlags(this)) {
            Store.setQuizFlag(this, q.id, null)
            updateFlagButton(q)
            return
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val input = EditText(this)
        input.hint = "Ne yanlış? (isteğe bağlı)"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        val box = LinearLayout(this)
        box.setPadding(pad, pad / 2, pad, 0)
        box.addView(input, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Bu soruda hata var")
            .setMessage("Soru telefonunda işaretlenir. Aile Halkası ana sayfasından işaretli soruların listesini paylaşabilirsin.")
            .setView(box)
            .setPositiveButton("İşaretle") { _, _ ->
                Store.setQuizFlag(this, q.id, input.text.toString())
                updateFlagButton(q)
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    /** İşaretli soruların listesi: paylaş ya da temizle. */
    private fun showFlags() {
        val report = QuizFlags.report(Store.quizFlags(this), QuizBank.all(this))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("İşaretlediğim sorular")
            .setMessage(report)
            .setPositiveButton("Paylaş") { _, _ ->
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                startActivity(Intent.createChooser(send, "İşaretli soruları paylaş"))
            }
            .setNeutralButton("Listeyi temizle") { _, _ ->
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Bütün işaretler silinsin mi?")
                    .setPositiveButton("Sil") { _, _ -> Store.clearQuizFlags(this); showHome() }
                    .setNegativeButton("Vazgeç", null)
                    .show()
            }
            .setNegativeButton("Kapat", null)
            .show()
    }

    /** Sırayla oynanıyorsa sorunun üstünde kimin sırası olduğunu yazar. */
    private fun showTurn(s: QuizSession) {
        val p = s.currentPlayer
        if (p == null) {
            tvTurn.visibility = View.GONE
        } else {
            tvTurn.text = "Sıra: ${s.players[p]}"
            tvTurn.visibility = View.VISIBLE
        }
    }

    private fun showResult(s: QuizSession) {
        Store.saveQuizSession(this, null)
        val msg = StringBuilder("${title(s)}: ${s.order.size} sorudan ${s.score} doğru.")
        if (s.players.isNotEmpty()) {
            msg.append("\n")
            // Kişi kişi: "Zeynep: 3 doğru"
            s.players.forEachIndexed { i, name -> msg.append("\n").append(name).append(": ").append(s.scores.getOrElse(i) { 0 }).append(" doğru") }
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Tur bitti 🎉")
            .setMessage(msg.toString())
            .setPositiveButton("Yeni tur") { _, _ -> if (s.review) startReview(s.players) else start(s.level, s.players) }
            .setNegativeButton("Ana sayfa") { _, _ -> showHome() }
            .setCancelable(false)
            .show()
    }

    /** Ağaç yeni bir aşamaya geçince kutla ve o aşamaya bağlı aile hedeflerini göster. */
    private fun celebrate(stage: Int) {
        val reached = Store.goals(this).filter { it.stage == stage }
        val msg = StringBuilder("Aile ağacımız artık: ${FamilyTree.STAGES[stage].name}!")
        if (reached.isNotEmpty()) {
            msg.append("\n\nHedefimize ulaştık:")
            reached.forEach { msg.append("\n🎯 ").append(it.text) }
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🌳 Ağacımız büyüdü")
            .setMessage(msg.toString())
            .setPositiveButton("Elhamdülillah", null)
            .show()
    }

    // ---------------- Aile hedefleri ----------------

    private fun renderGoals(currentStage: Int) {
        goalList.removeAllViews()
        val goals = Store.goals(this)
        if (goals.isEmpty()) {
            val tv = TextView(this)
            tv.text = "Henüz hedef yok. Örnek: \"Ağacımız Meyveli ağaç olunca birlikte dondurma yemeye gidiyoruz.\""
            tv.setTextColor(getColor(R.color.muted))
            tv.textSize = 14f
            tv.setPadding(16, 16, 16, 16)
            goalList.addView(tv)
            return
        }
        goals.forEachIndexed { i, g ->
            val done = currentStage >= g.stage
            val tv = TextView(this)
            tv.text = (if (done) "✅ " else "🎯 ") + g.text + "\n" +
                (if (done) "Ulaşıldı · " else "Hedef aşama: ") +
                (FamilyTree.STAGES.getOrNull(g.stage)?.name ?: "?")
            tv.setTextColor(getColor(if (done) R.color.gold else R.color.text))
            tv.textSize = 15f
            tv.setPadding(16, 18, 16, 18)
            tv.setOnLongClickListener {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(g.text)
                    .setItems(arrayOf("Düzenle", "Sil")) { _, which ->
                        if (which == 0) {
                            addGoal(editIndex = i)
                        } else {
                            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                                .setTitle("Hedef silinsin mi?")
                                .setMessage(g.text)
                                .setPositiveButton("Sil") { _, _ ->
                                    Store.saveGoals(this, Store.goals(this).filterIndexed { j, _ -> j != i })
                                    showHome()
                                }
                                .setNegativeButton("Vazgeç", null)
                                .show()
                        }
                    }
                    .show()
                true
            }
            goalList.addView(tv)
        }
        val hint = TextView(this)
        hint.text = "Düzenlemek ya da silmek için hedefe basılı tut."
        hint.setTextColor(getColor(R.color.muted))
        hint.textSize = 12f
        hint.setPadding(16, 4, 16, 8)
        goalList.addView(hint)
    }

    /** Yeni hedef ekler; [editIndex] verilirse o hedefi düzenler. */
    private fun addGoal(editIndex: Int? = null) {
        val editing = editIndex?.let { Store.goals(this).getOrNull(it) }
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val pad = (20 * resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad / 2, pad, 0)

        val input = EditText(this)
        input.hint = "Ör: Birlikte dondurma yemeye gidiyoruz"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        editing?.let { input.setText(it.text) }
        box.addView(input)

        val label = TextView(this)
        label.text = "Ağacımız hangi aşamaya gelince?"
        label.setPadding(0, pad / 2, 0, 0)
        box.addView(label)

        val stages = FamilyTree.STAGES.drop(1) // "Tohum" hariç
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item,
            stages.map { "${it.name} (${it.minPoints} doğru)" },
        )
        editing?.let { spinner.setSelection((it.stage - 1).coerceIn(0, stages.size - 1)) }
        box.addView(spinner)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (editing != null) "Hedefi düzenle" else "Yeni aile hedefi")
            .setView(box)
            .setPositiveButton(if (editing != null) "Kaydet" else "Ekle") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    val goal = FamilyGoal(text, spinner.selectedItemPosition + 1)
                    val list = Store.goals(this)
                    val updated = if (editIndex != null && editIndex in list.indices) {
                        list.mapIndexed { j, g -> if (j == editIndex) goal else g }
                    } else {
                        list + goal
                    }
                    Store.saveGoals(this, updated)
                    showHome()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }
}
