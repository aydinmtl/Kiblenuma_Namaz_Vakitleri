package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.window.OnBackInvokedDispatcher

/**
 * Aile Halkası: bilgi yarışması.
 * - Seviye seçilir (Kolay/Orta/Zor), sorular her yeni yarışmada karışık sırayla gelir.
 * - Cevaptan sonra doğrusu ve kısa açıklama gösterilir, "Sıradaki soru" ile devam edilir.
 * - İstenildiği an kapatılabilir; tekrar girince "kaldığın yerden devam" sorulur.
 * - Her doğru cevap aile ağacını büyütür; ağaç aşamalarına aile hedefleri bağlanabilir.
 */
class QuizActivity : Activity() {

    private lateinit var homePane: View
    private lateinit var playPane: View
    private lateinit var tree: TreeView
    private lateinit var tvTree: TextView
    private lateinit var goalList: LinearLayout
    private lateinit var tvProgress: TextView
    private lateinit var bar: ProgressBar
    private lateinit var tvQuestion: TextView
    private lateinit var options: LinearLayout
    private lateinit var tvInfo: TextView
    private lateinit var btnNext: Button

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
        tvProgress = findViewById(R.id.tvProgress)
        bar = findViewById(R.id.bar)
        tvQuestion = findViewById(R.id.tvQuestion)
        options = findViewById(R.id.options)
        tvInfo = findViewById(R.id.tvInfo)
        btnNext = findViewById(R.id.btnNext)

        setupLevelButton(R.id.btnEasy, QuizLevel.EASY)
        setupLevelButton(R.id.btnMedium, QuizLevel.MEDIUM)
        setupLevelButton(R.id.btnHard, QuizLevel.HARD)
        findViewById<View>(R.id.btnAddGoal).setOnClickListener { addGoal() }
        findViewById<View>(R.id.btnClose).setOnClickListener { showHome() }
        btnNext.setOnClickListener { showQuestion() }

        showHome()
    }

    override fun onResume() {
        super.onResume()
        // Yarım kalmış yarışma varsa sor (ekran her açıldığında bir kez).
        val s = Store.quizSession(this)
        if (!askedResume && playPane.visibility != View.VISIBLE && s != null && !s.finished) {
            askedResume = true
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Kaldığın yerden devam etmek ister misin?")
                .setMessage("${s.level.title} seviye · ${s.index + 1}. soru / ${s.order.size} · ${s.score} doğru")
                .setPositiveButton("Devam et") { _, _ -> resume(s) }
                .setNegativeButton("Baştan başla") { _, _ -> start(s.level) }
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
        b.text = "${level.title}\n${level.hint} · $count soru"
        b.setOnClickListener {
            val s = Store.quizSession(this)
            if (s != null && !s.finished) {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Yarım kalan yarışma var")
                    .setMessage("${s.level.title} seviyede ${s.index + 1}. sorudasın. Ne yapalım?")
                    .setPositiveButton("Devam et") { _, _ -> resume(s) }
                    .setNegativeButton("Yeni ${level.title} yarışma") { _, _ -> start(level) }
                    .show()
            } else {
                start(level)
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
    }

    // ---------------- Yarışma ----------------

    private fun start(level: QuizLevel) {
        val s = QuizSession.newGame(this, level)
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
        tvProgress.text = "${s.level.title} · Soru ${s.index + 1} / ${s.order.size} · ✓ ${s.score}"
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

        // Doğru şıkkı yeşil, yanlış seçileni kırmızı göster
        for (i in 0 until options.childCount) {
            val b = options.getChildAt(i) as Button
            b.isEnabled = false
            when (b.text.toString()) {
                q.correct -> b.setBackgroundResource(R.drawable.opt_correct)
                chosen -> b.setBackgroundResource(R.drawable.opt_wrong)
            }
        }
        tvInfo.text = (if (ok) "✓ Doğru!" else "✗ Doğru cevap: ${q.correct}") + if (q.info.isNotEmpty()) "\n${q.info}" else ""
        tvInfo.setTextColor(getColor(if (ok) R.color.gold else R.color.text))
        tvInfo.visibility = View.VISIBLE

        // Hemen kaydet: kapatılırsa bir sonraki sorudan devam edilir.
        val updated = s.copy(index = s.index + 1, score = s.score + if (ok) 1 else 0)
        session = updated
        Store.saveQuizSession(this, if (updated.finished) null else updated)
        if (ok) Store.addTreePoint(this)

        btnNext.text = if (updated.finished) "Sonucu gör ›" else "Sıradaki soru ›"
        btnNext.visibility = View.VISIBLE
        tvProgress.text = "${s.level.title} · Soru ${s.index + 1} / ${s.order.size} · ✓ ${updated.score}"
        bar.progress = updated.index

        val newStage = FamilyTree.stageIndex(Store.treePoints(this))
        if (newStage > oldStage) celebrate(newStage)
    }

    private fun showResult(s: QuizSession) {
        Store.saveQuizSession(this, null)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Yarışma bitti 🎉")
            .setMessage("${s.level.title} seviye: ${s.order.size} sorudan ${s.score} doğru.")
            .setPositiveButton("Yeniden başla") { _, _ -> start(s.level) }
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
                (if (done) "Ulaşıldı · " else "Hedef aşama: ") + FamilyTree.STAGES[g.stage].name
            tv.setTextColor(getColor(if (done) R.color.gold else R.color.text))
            tv.textSize = 15f
            tv.setPadding(16, 18, 16, 18)
            tv.setOnLongClickListener {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle("Hedef silinsin mi?")
                    .setMessage(g.text)
                    .setPositiveButton("Sil") { _, _ ->
                        Store.saveGoals(this, Store.goals(this).filterIndexed { j, _ -> j != i })
                        showHome()
                    }
                    .setNegativeButton("Vazgeç", null)
                    .show()
                true
            }
            goalList.addView(tv)
        }
        val hint = TextView(this)
        hint.text = "Silmek için hedefe basılı tut."
        hint.setTextColor(getColor(R.color.muted))
        hint.textSize = 12f
        hint.setPadding(16, 4, 16, 8)
        goalList.addView(hint)
    }

    private fun addGoal() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        val pad = (20 * resources.displayMetrics.density).toInt()
        box.setPadding(pad, pad / 2, pad, 0)

        val input = EditText(this)
        input.hint = "Ör: Birlikte dondurma yemeye gidiyoruz"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
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
        box.addView(spinner)

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Yeni aile hedefi")
            .setView(box)
            .setPositiveButton("Ekle") { _, _ ->
                val text = input.text.toString().trim()
                if (text.isNotEmpty()) {
                    Store.saveGoals(this, Store.goals(this) + FamilyGoal(text, spinner.selectedItemPosition + 1))
                    showHome()
                }
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }
}
