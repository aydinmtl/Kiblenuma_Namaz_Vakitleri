package com.aydinmtl.namazvakitleri

import android.animation.ObjectAnimator
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher
import java.util.Locale

/**
 * Namaz Öğreniyorum: çocuklar için oyun.
 *
 * Akış: figür seçilir (Ahmet ya da Zeynep) → her adımda bir soru gelir → doğru cevapta figür o
 * adımı canlandırır (ellerini yıkar, tekbir alır, rükûya eğilir...) → "Devam" ile sıradaki soru.
 * Yanlış cevapta ceza yok: figür başını sallar, çocuk tekrar dener; iki yanlıştan sonra doğru
 * şık altın çerçeveyle belli olur. Selamla namaz biter, konfeti yağar.
 *
 * Kaldığı adım kaydedilir; çıkıp girince "Kaldığın yerden devam et" denebilir.
 * Adımlar ve sorular PrayerGame.kt'de; 3B sahne PrayerStageView.kt ve assets/namazoyun3d/ içindedir
 * (3B çalışmayan cihazlar için eski 2B çizim PrayerFigureView.kt'de durur).
 */
class PrayerGameActivity : Activity() {

    private lateinit var startPane: View
    private lateinit var gamePane: View
    private lateinit var figure: PrayerStageView
    private lateinit var tvChapter: TextView
    private lateinit var bar: ProgressBar
    private lateinit var tvStepTitle: TextView
    private lateinit var questionBox: View
    private lateinit var tvQuestion: TextView
    private lateinit var options: LinearLayout
    private lateinit var tvInfo: TextView
    private lateinit var btnNext: Button
    private lateinit var finishBox: View
    private lateinit var btnSpeak: TextView
    private lateinit var tvRecite: TextView

    private var girl = false
    private var index = 0
    private var busy = false // figür hareket ederken yeni cevap alınmaz
    private var wrongCount = 0
    private val dp: Float by lazy { resources.displayMetrics.density }

    // Sesli okuma (okuma bilmeyen küçükler için): telefonun kendi Türkçe sesi
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    // Duaların, surelerin ve tekbirin uygulamayla gelen ses kayıtları (dosya yoksa sessiz geçer)
    private lateinit var voice: PrayerVoice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_prayer_game)
        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { goBack() }
        }
        Ui.fitSystemBars(this, findViewById(R.id.root))

        startPane = findViewById(R.id.startPane)
        gamePane = findViewById(R.id.gamePane)
        figure = findViewById(R.id.figure)
        tvChapter = findViewById(R.id.tvChapter)
        bar = findViewById(R.id.bar)
        tvStepTitle = findViewById(R.id.tvStepTitle)
        questionBox = findViewById(R.id.questionBox)
        tvQuestion = findViewById(R.id.tvQuestion)
        options = findViewById(R.id.options)
        tvInfo = findViewById(R.id.tvInfo)
        btnNext = findViewById(R.id.btnNext)
        finishBox = findViewById(R.id.finishBox)
        btnSpeak = findViewById(R.id.btnSpeak)
        tvRecite = findViewById(R.id.tvRecite)
        // Figür okurken okuma kartı kelime kelime ilerler; karta ya da figüre dokununca okuma biter
        // Okumanın ilk kelimesinde o duanın ses kaydı başlar
        voice = PrayerVoice(this)
        figure.onRecite = { r, n ->
            if (n == 1) voice.play(PrayerSounds.of(r))
            showRecite(r, n)
        }
        figure.onCaption = { c -> voice.play(PrayerSounds.ofCaption(c)) }
        figure.onSkip = { voice.stop() }
        figure.reciteMs = { r -> voice.durationMs(PrayerSounds.of(r))?.plus(300) }
        tvRecite.setOnClickListener { figure.fastForward() }

        girl = Store.prayerGirl(this) ?: false
        findViewById<View>(R.id.cardBoy).setOnClickListener { choose(false) }
        findViewById<View>(R.id.cardGirl).setOnClickListener { choose(true) }
        choose(girl, save = false)

        findViewById<View>(R.id.btnBack).setOnClickListener { goBack() }
        findViewById<View>(R.id.btnStart).setOnClickListener { startGame(Store.prayerStep(this)) }
        findViewById<View>(R.id.btnRestart).setOnClickListener { startGame(0) }
        findViewById<View>(R.id.btnNamazOnly).setOnClickListener { startGame(PrayerGame.NAMAZ_START) }
        btnNext.setOnClickListener { showStep(index + 1) }
        btnSpeak.setOnClickListener { speakCurrent() }
        findViewById<View>(R.id.btnAgain).setOnClickListener { startGame(0) }
        findViewById<View>(R.id.btnExit).setOnClickListener { finish() }

        showStart()
    }

    // Ekrandan çıkınca 3B çizimi durdur, dönünce devam ettir
    override fun onPause() { voice.stop(); figure.pause(); super.onPause() }
    override fun onResume() { super.onResume(); figure.resume() }

    override fun onDestroy() {
        figure.destroy()
        voice.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = goBack()

    /** Oyundaysak başlangıç ekranına dön (adım kayıtlı kalır), değilse çık. */
    private fun goBack() {
        if (gamePane.visibility == View.VISIBLE) showStart() else finish()
    }

    // ---------------- Başlangıç ----------------

    private fun choose(g: Boolean, save: Boolean = true) {
        girl = g
        if (save) Store.setPrayerGirl(this, g)
        val boy = findViewById<View>(R.id.cardBoy)
        val gl = findViewById<View>(R.id.cardGirl)
        boy.isSelected = !g
        gl.isSelected = g
        // Seçilen kart hafifçe büyür
        boy.animate().scaleX(if (!g) 1.03f else 0.97f).scaleY(if (!g) 1.03f else 0.97f).setDuration(180).start()
        gl.animate().scaleX(if (g) 1.03f else 0.97f).scaleY(if (g) 1.03f else 0.97f).setDuration(180).start()
    }

    private fun showStart() {
        tts?.stop()
        voice.stop()
        gamePane.visibility = View.GONE
        startPane.visibility = View.VISIBLE
        btnSpeak.visibility = View.GONE
        fadeIn(startPane)
        val saved = Store.prayerStep(this).coerceIn(0, PrayerGame.STEPS.size - 1)
        val btn = findViewById<Button>(R.id.btnStart)
        btn.text = if (saved > 0) "▶  Kaldığın yerden devam et" else "▶  Başla"
        findViewById<View>(R.id.btnRestart).visibility = if (saved > 0) View.VISIBLE else View.GONE
        val wins = Store.prayerWins(this)
        val tvWins = findViewById<TextView>(R.id.tvWins)
        tvWins.visibility = if (wins > 0) View.VISIBLE else View.GONE
        tvWins.text = "🏆 Namazı $wins kez tamamladın"
    }

    private fun startGame(from: Int) {
        val i = from.coerceIn(0, PrayerGame.STEPS.size - 1)
        Store.setPrayerStep(this, i)
        startPane.visibility = View.GONE
        gamePane.visibility = View.VISIBLE
        btnSpeak.visibility = View.VISIBLE
        fadeIn(gamePane)
        figure.girl = girl
        figure.show(PrayerGame.restingPose(i), PrayerGame.sceneAt(i))
        showStep(i)
    }

    private fun fadeIn(v: View) {
        v.alpha = 0f
        v.translationY = 16 * dp
        v.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(260)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    // ---------------- Adımlar ----------------

    private fun showStep(i: Int) {
        if (i >= PrayerGame.STEPS.size) return
        index = i
        busy = false
        wrongCount = 0
        tts?.stop()
        val step = PrayerGame.STEPS[i]
        val (n, total) = PrayerGame.positionInChapter(i)
        tvChapter.text = "${step.chapter.icon} ${step.chapter.title} · Adım $n / $total"
        bar.max = PrayerGame.STEPS.size * 100
        ObjectAnimator.ofInt(bar, "progress", bar.progress, i * 100).setDuration(450).start()

        finishBox.visibility = View.GONE
        tvRecite.visibility = View.GONE
        questionBox.visibility = View.VISIBLE
        tvStepTitle.visibility = View.INVISIBLE
        tvInfo.visibility = View.GONE
        btnNext.visibility = View.GONE

        tvQuestion.text = step.question
        tvQuestion.alpha = 0f
        tvQuestion.translationX = 40 * dp
        tvQuestion.animate().alpha(1f).translationX(0f).setStartDelay(0).setDuration(280)
            .setInterpolator(DecelerateInterpolator()).start()

        options.removeAllViews()
        step.options.shuffled().forEachIndexed { k, opt ->
            val b = Button(this)
            b.text = opt
            b.tag = opt
            b.isAllCaps = false
            b.textSize = 18f
            b.setTextColor(getColor(R.color.text))
            b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            b.setBackgroundResource(R.drawable.opt_normal)
            b.stateListAnimator = null
            val pad = (16 * dp).toInt()
            b.setPadding(pad, pad, pad, pad)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = (8 * dp).toInt()
            b.layoutParams = lp
            b.setOnClickListener { answer(b, step, opt) }
            b.alpha = 0f
            b.translationY = 18 * dp
            b.animate().alpha(1f).translationY(0f).setStartDelay(140L + 70L * k).setDuration(240)
                .setInterpolator(DecelerateInterpolator()).start()
            options.addView(b)
        }
    }

    private fun answer(b: Button, step: PrayerStep, chosen: String) {
        if (busy) return
        if (chosen != step.correct) {
            wrongCount++
            b.isEnabled = false
            b.setBackgroundResource(R.drawable.opt_wrong)
            b.text = "✗  $chosen"
            b.contentDescription = "$chosen, yanlış"
            shake(b)
            figure.oops()
            tvInfo.text = if (wrongCount >= 2) "Neredeyse! Altın çerçeveli şıkka bir bak. 🙂" else "Olmadı, bir daha dene. Sen yaparsın! 💪"
            tvInfo.setTextColor(getColor(R.color.text))
            tvInfo.visibility = View.VISIBLE
            if (wrongCount >= 2) hintCorrect(step)
            return
        }

        // Doğru: şıklar kilitlenir, figür adımı canlandırır
        busy = true
        for (k in 0 until options.childCount) {
            val o = options.getChildAt(k) as Button
            o.isEnabled = false
            if (o.tag == step.correct) {
                o.setBackgroundResource(R.drawable.opt_correct)
                o.text = "✓  ${step.correct}"
                o.contentDescription = "${step.correct}, doğru"
                pulse(o)
            } else {
                o.animate().alpha(0.4f).setStartDelay(0).setDuration(200).start()
            }
        }
        val (n, _) = PrayerGame.positionInChapter(index)
        tvStepTitle.text = "Adım $n · ${step.title}"
        tvStepTitle.visibility = View.VISIBLE
        tvStepTitle.alpha = 0f
        tvStepTitle.scaleX = 0.7f
        tvStepTitle.scaleY = 0.7f
        tvStepTitle.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(350)
            .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()

        tvInfo.text = "✓ Aferin!\n${step.info}" + if (step.source.isNotEmpty()) "\nKaynak: ${step.source}" else ""
        tvInfo.setTextColor(getColor(R.color.gold))
        tvInfo.visibility = View.VISIBLE
        tvInfo.alpha = 0f
        tvInfo.animate().alpha(1f).setStartDelay(200).setDuration(300).start()

        val last = index == PrayerGame.STEPS.size - 1
        figure.play(step.moves, step.chapter, step.part) {
            if (isFinishing) return@play
            if (last) {
                finishGame()
            } else {
                Store.setPrayerStep(this, index + 1)
                btnNext.visibility = View.VISIBLE
                btnNext.alpha = 0f
                btnNext.animate().alpha(1f).setStartDelay(0).setDuration(250).start()
            }
        }
    }

    /**
     * Okuma kartı: başlık, okunuşun okunmuş kısmı altın renkli ve kalın, kalanı soluk; altında anlamı.
     * [n]: şu ana kadar okunan kelime sayısı.
     */
    private fun showRecite(r: Recitation, n: Int) {
        val words = r.words
        val sb = SpannableStringBuilder()
        fun span(text: String, vararg spans: Any) {
            val start = sb.length
            sb.append(text)
            for (sp in spans) sb.setSpan(sp, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        span("📖 ${r.title}\n", ForegroundColorSpan(getColor(R.color.gold)), StyleSpan(android.graphics.Typeface.BOLD), RelativeSizeSpan(0.85f))
        span(words.take(n).joinToString(" "), ForegroundColorSpan(getColor(R.color.gold)), StyleSpan(android.graphics.Typeface.BOLD))
        if (n < words.size) span(" " + words.drop(n).joinToString(" "), ForegroundColorSpan(0x66F2F2F2))
        span("\n\nAnlamı: ${r.meaning}", ForegroundColorSpan(getColor(R.color.muted)), RelativeSizeSpan(0.8f))
        if (r.source.isNotEmpty()) span("\nKaynak: ${r.source}", ForegroundColorSpan(getColor(R.color.muted)), RelativeSizeSpan(0.75f))
        tvRecite.text = sb
        tvRecite.contentDescription = "${r.title}. ${r.text}. Anlamı: ${r.meaning}"
        if (tvRecite.visibility != View.VISIBLE) {
            tvRecite.visibility = View.VISIBLE
            fadeIn(tvRecite)
        }
    }

    /** İki yanlıştan sonra doğru şık altın çerçeve alır ve hafifçe "nabız" atar. */
    private fun hintCorrect(step: PrayerStep) {
        for (k in 0 until options.childCount) {
            val o = options.getChildAt(k) as Button
            if (o.tag == step.correct) {
                o.setBackgroundResource(R.drawable.opt_hint)
                pulse(o)
            }
        }
    }

    private fun finishGame() {
        Store.setPrayerStep(this, 0)
        Store.addPrayerWin(this)
        bar.progress = bar.max
        figure.celebrate()
        questionBox.visibility = View.GONE
        tvStepTitle.visibility = View.INVISIBLE
        btnSpeak.visibility = View.GONE
        val name = if (girl) "Zeynep" else "Ahmet"
        findViewById<TextView>(R.id.tvFinishTitle).text = "Maşallah! 🎉"
        findViewById<TextView>(R.id.tvFinishText).text =
            "Bütün soruları bildin. $name seninle birlikte abdest aldı ve sabah namazını baştan sona kıldı.\n\n" +
            "Şimdi sıra sende: bir dahaki namazda bunları birlikte yapalım!"
        finishBox.visibility = View.VISIBLE
        fadeIn(finishBox)
        findViewById<View>(R.id.root).post { (findViewById<View>(R.id.root) as android.widget.ScrollView).smoothScrollTo(0, 0) }
    }

    // ---------------- Küçük canlandırmalar ----------------

    private fun pulse(v: View) {
        v.animate().scaleX(1.05f).scaleY(1.05f).setStartDelay(0).setDuration(150).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(170).start()
        }.start()
    }

    private fun shake(v: View) {
        val d = 10 * dp
        ObjectAnimator.ofFloat(v, "translationX", 0f, -d, d, -d * 0.7f, d * 0.7f, -d * 0.3f, 0f).setDuration(420).start()
    }

    // ---------------- Sesli okuma ----------------

    /** Cevaptan önce soruyu ve şıkları, cevaptan sonra açıklamayı okur. */
    private fun speakCurrent() {
        if (gamePane.visibility != View.VISIBLE || index >= PrayerGame.STEPS.size) return
        val step = PrayerGame.STEPS[index]
        val text = if (busy) {
            QuizSpeech.clean(tvStepTitle.text.toString() + ". " + step.info)
        } else {
            val shown = (0 until options.childCount).map { (options.getChildAt(it).tag as? String).orEmpty() }
            QuizSpeech.question(step.question, shown)
        }
        speak(text)
    }

    private fun speak(text: String) {
        val engine = tts
        if (engine != null && ttsReady) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prayer")
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
            pendingSpeech?.let { t.speak(it, TextToSpeech.QUEUE_FLUSH, null, "prayer") }
            pendingSpeech = null
        }
    }
}
