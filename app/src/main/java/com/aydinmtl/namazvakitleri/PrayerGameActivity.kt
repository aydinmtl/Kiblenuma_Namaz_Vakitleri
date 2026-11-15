package com.aydinmtl.namazvakitleri

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.media.AudioManager
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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import android.window.OnBackInvokedDispatcher

/**
 * Namaz Öğreniyorum: çocuklar için oyun.
 *
 * Akış: figür seçilir (Ahmet ya da Zeynep) → her adımda bir soru gelir → doğru cevapta figür o
 * adımı canlandırır (ellerini yıkar, tekbir alır, rükûya eğilir...) → "Devam" ile sıradaki soru.
 * Yanlış cevapta ceza yok: figür başını sallar, çocuk tekrar dener; iki yanlıştan sonra doğru
 * şık altın çerçeveyle belli olur. Selamla namaz biter, konfeti yağar.
 *
 * Başlangıç ekranında Abdest ve Namaz ayrı düğmelerdir. Her bölümde kalınan adım ayrı kaydedilir;
 * yarım kalmış bölüme basınca "kaldığın yerden mi, baştan mı" diye sorulur. Namaz, çocuğun
 * seccadesini serdiği kısa bir canlandırmayla başlar. Abdest bitince "Namaza geçelim mi?" diye sorulur.
 * Adımlar ve sorular PrayerGame.kt'de; 3B sahne PrayerStageView.kt ve assets/namazoyun3d/ içindedir
 * (3B çalışmayan cihazlar için eski 2B çizim PrayerFigureView.kt'de durur).
 */
class PrayerGameActivity : LangActivity() {

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

    // Sesli okuma (okuma bilmeyen küçükler için): telefonun seçili dildeki sesi
    private val learning by lazy { LearningText(this) }

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeech: String? = null

    // Duaların, surelerin ve tekbirin uygulamayla gelen ses kayıtları (dosya yoksa sessiz geçer)
    private lateinit var voice: PrayerVoice
    private var soundOn = true    // sesli mod: kayıtlar çalınır; sessiz modda yalnızca yazı ilerler
    private var skipStep = false  // "Okunuşu atla"ya basıldı: bu adımın kalan okumaları da atlanır
    private lateinit var btnSkip: TextView
    private lateinit var swSound: Switch
    private lateinit var btnLater: TextView

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
        soundOn = Store.prayerSound(this)
        volumeControlStream = AudioManager.STREAM_MUSIC // ses tuşları oyundayken medya sesini ayarlasın
        btnSkip = findViewById(R.id.btnSkip)
        swSound = findViewById(R.id.swSound)
        btnLater = findViewById(R.id.btnLater)
        figure.onRecite = { r, n ->
            if (n == 1) {
                if (skipStep) {
                    figure.fastForward() // atla denmişti: bu adımın sonraki okuması da hemen geçilir
                } else {
                    if (soundOn) {
                        stopSpeech()
                        if (learning.isIntention(r)) {
                            if (Lang.code(this) != "tr") speak(r.text)
                            else voice.play(PrayerSounds.of(Recitations.NIYET))
                        } else voice.play(PrayerSounds.of(r))
                    }
                    btnSkip.visibility = View.VISIBLE
                }
            }
            showRecite(r, n)
        }
        figure.onCaption = { c -> if (soundOn && !skipStep) voice.play(PrayerSounds.ofCaption(c)) }
        figure.onSkip = { voice.stop(); stopSpeech() }
        // Sesli modda okuma, kaydın uzunluğu kadar sürer; sessiz modda kelime sayısına göre (eskisi gibi)
        figure.reciteMs = { r ->
            val intention = learning.isIntention(r)
            if (soundOn && (!intention || Lang.code(this) == "tr")) {
                voice.durationMs(PrayerSounds.of(if (intention) Recitations.NIYET else r))?.plus(300)
            } else null
        }
        btnSkip.setOnClickListener {
            skipStep = true
            btnSkip.visibility = View.GONE
            figure.fastForward()
        }
        swSound.isChecked = soundOn
        swSound.setOnCheckedChangeListener { _, on ->
            soundOn = on
            if (!on) { voice.stop(); stopSpeech() }
            Store.setPrayerSound(this, on)
            showSoundChoice()
        }
        showSoundChoice()
        tvRecite.setOnClickListener { figure.fastForward() }

        girl = Store.prayerGirl(this) ?: false
        findViewById<View>(R.id.cardBoy).setOnClickListener { choose(false) }
        findViewById<View>(R.id.cardGirl).setOnClickListener { choose(true) }
        choose(girl, save = false)

        findViewById<View>(R.id.btnBack).setOnClickListener { goBack() }
        findViewById<View>(R.id.btnAbdest).setOnClickListener { openChapter(PrayerChapter.ABDEST) }
        findViewById<View>(R.id.btnNamaz).setOnClickListener { openChapter(PrayerChapter.NAMAZ) }
        btnNext.setOnClickListener {
            // Abdestin son adımından sonra "Namaza geçelim mi?" sorulur; evet denince namaz başlar
            if (index + 1 == PrayerGame.NAMAZ_START) startGame(PrayerGame.NAMAZ_START, fromAbdest = true)
            else showStep(index + 1)
        }
        btnLater.setOnClickListener { showStart() }
        btnSpeak.setOnClickListener { speakCurrent() }
        findViewById<View>(R.id.btnAgain).setOnClickListener { startGame(PrayerGame.NAMAZ_START) }
        findViewById<View>(R.id.btnExit).setOnClickListener { finish() }

        showStart()
    }

    // Ekrandan çıkınca 3B çizimi durdur, dönünce devam ettir
    override fun onPause() { voice.stop(); stopSpeech(); figure.pause(); super.onPause() }
    override fun onResume() { super.onResume(); figure.resume() }

    override fun onDestroy() {
        figure.destroy()
        voice.stop()
        stopSpeech()
        ttsReady = false
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

    /** Sesli okuma anahtarının altındaki açıklama. */
    private fun showSoundChoice() {
        findViewById<TextView>(R.id.tvSoundInfo).text =
            if (soundOn) getString(R.string.learn_ui_082) else getString(R.string.learn_ui_083)
    }

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
        showChapterButtons()
    }

    /**
     * Abdest ve Namaz düğmelerinin yazısı: üstte bölümün adı, altında kalınan yer.
     * Abdesti her zaman Ahmet alır; Zeynep seçiliyken Abdest düğmesi kapalıdır. Böylece
     * abdest alan Ahmet'ten namaz kılan Zeynep'e geçiş olmaz.
     */
    private fun showChapterButtons() {
        for (ch in PrayerChapter.values()) {
            val btn = findViewById<Button>(if (ch == PrayerChapter.ABDEST) R.id.btnAbdest else R.id.btnNamaz)
            val start = PrayerGame.chapterStart(ch)
            val saved = Store.prayerStep(this, ch)
            val total = PrayerGame.chapterEnd(ch) - start
            val status = if (saved > start) getString(R.string.learn_ui_084, saved - start + 1, total) else getString(R.string.learn_ui_085, total)
            val sb = SpannableStringBuilder("${ch.icon}  ${learning.chapter(ch)}")
            sb.setSpan(StyleSpan(android.graphics.Typeface.BOLD), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(1.2f), 0, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val from = sb.length
            sb.append("\n").append(status)
            sb.setSpan(RelativeSizeSpan(0.85f), from, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            btn.text = sb
            btn.contentDescription = "${learning.chapter(ch)}. $status"
        }
        val abdest = findViewById<Button>(R.id.btnAbdest)
        abdest.isEnabled = !girl
        abdest.alpha = if (girl) 0.4f else 1f
        findViewById<View>(R.id.tvAbdestNote).visibility = if (girl) View.VISIBLE else View.GONE
    }

    /**
     * Abdest ya da Namaz düğmesine basıldı. O bölümde yarım kalmış oyun varsa
     * "kaldığın yerden mi, baştan mı" diye sorar; yoksa doğrudan baştan başlar.
     */
    private fun openChapter(ch: PrayerChapter) {
        val start = PrayerGame.chapterStart(ch)
        val saved = Store.prayerStep(this, ch)
        if (saved <= start) {
            startGame(start)
            return
        }
        val total = PrayerGame.chapterEnd(ch) - start
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("${ch.icon} ${learning.chapter(ch)}")
            .setMessage(getString(R.string.learn_ui_086, saved - start + 1, total))
            .setPositiveButton(getString(R.string.learn_ui_087)) { _, _ -> startGame(saved) }
            .setNegativeButton(getString(R.string.learn_ui_088)) { _, _ -> startGame(start) }
            .show()
    }

    private fun showStart() {
        stopSpeech()
        voice.stop()
        gamePane.visibility = View.GONE
        startPane.visibility = View.VISIBLE
        btnSpeak.visibility = View.GONE
        fadeIn(startPane)
        showChapterButtons()
        val wins = Store.prayerWins(this)
        val tvWins = findViewById<TextView>(R.id.tvWins)
        tvWins.visibility = if (wins > 0) View.VISIBLE else View.GONE
        tvWins.text = getString(R.string.learn_ui_089, wins)
    }

    /**
     * Oyunu [from]. adımdan başlatır. Namazın ilk adımından başlanıyorsa önce giriş
     * canlandırması oynar (çocuk seccadesini serer), ilk soru ondan sonra gelir.
     * [fromAbdest]: abdest yeni bitti ve aynı oyunda namaza geçiliyor.
     */
    private fun startGame(from: Int, fromAbdest: Boolean = false) {
        val i = from.coerceIn(0, PrayerGame.STEPS.size - 1)
        Store.setPrayerStep(this, PrayerGame.STEPS[i].chapter, i)
        startPane.visibility = View.GONE
        gamePane.visibility = View.VISIBLE
        btnSpeak.visibility = View.VISIBLE
        if (!fromAbdest) fadeIn(gamePane)
        figure.girl = girl
        if (i == PrayerGame.NAMAZ_START) {
            showIntro()
            figure.intro(fromAbdest) {
                // Giriş sürerken geri dönüldüyse soru gösterilmez
                if (!isFinishing && gamePane.visibility == View.VISIBLE) showStep(i)
            }
        } else {
            figure.show(PrayerGame.restingPose(i), PrayerGame.sceneAt(i))
            showStep(i)
        }
    }

    /** Giriş canlandırması oynarken ekran: soru yok, yalnızca ne olduğunu anlatan başlık. */
    private fun showIntro() {
        index = PrayerGame.NAMAZ_START
        busy = true
        stopSpeech()
        voice.stop()
        val ch = PrayerChapter.NAMAZ
        tvChapter.text = getString(R.string.learn_ui_090, ch.icon, learning.chapter(ch))
        bar.progress = 0
        finishBox.visibility = View.GONE
        tvRecite.visibility = View.GONE
        btnSkip.visibility = View.GONE
        questionBox.visibility = View.GONE
        tvStepTitle.text = getString(R.string.learn_ui_091)
        tvStepTitle.alpha = 1f
        tvStepTitle.scaleX = 1f
        tvStepTitle.scaleY = 1f
        tvStepTitle.visibility = View.VISIBLE
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
        stopSpeech()
        val step = learning.step(i)
        val (n, total) = PrayerGame.positionInChapter(i)
        tvChapter.text = getString(R.string.learn_ui_092, step.chapter.icon, learning.chapter(step.chapter), n, total)
        // İlerleme çubuğu bölümün kendi içindeki ilerlemeyi gösterir
        bar.max = total * 100
        ObjectAnimator.ofInt(bar, "progress", bar.progress.coerceAtMost(bar.max), (n - 1) * 100).setDuration(450).start()

        finishBox.visibility = View.GONE
        tvRecite.visibility = View.GONE
        btnSkip.visibility = View.GONE
        skipStep = false
        voice.stop()
        questionBox.visibility = View.VISIBLE
        tvStepTitle.visibility = View.INVISIBLE
        tvInfo.visibility = View.GONE
        btnNext.visibility = View.GONE
        btnNext.text = getString(R.string.learn_ui_093)
        btnLater.visibility = View.GONE

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
            b.contentDescription = getString(R.string.learn_ui_094, chosen)
            shake(b)
            figure.oops()
            tvInfo.text = if (wrongCount >= 2) getString(R.string.learn_ui_095) else getString(R.string.learn_ui_096)
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
                o.contentDescription = getString(R.string.learn_ui_097, step.correct)
                pulse(o)
            } else {
                o.animate().alpha(0.4f).setStartDelay(0).setDuration(200).start()
            }
        }
        val (n, _) = PrayerGame.positionInChapter(index)
        tvStepTitle.text = getString(R.string.learn_ui_098, n, step.title)
        tvStepTitle.visibility = View.VISIBLE
        tvStepTitle.alpha = 0f
        tvStepTitle.scaleX = 0.7f
        tvStepTitle.scaleY = 0.7f
        tvStepTitle.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0).setDuration(350)
            .setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()

        tvInfo.text = getString(R.string.learn_ui_099, step.info) + if (step.source.isNotEmpty()) getString(R.string.learn_ui_100, step.source) else ""
        tvInfo.setTextColor(getColor(R.color.gold))
        tvInfo.visibility = View.VISIBLE
        tvInfo.alpha = 0f
        tvInfo.animate().alpha(1f).setStartDelay(200).setDuration(300).start()

        val last = index == PrayerGame.STEPS.size - 1
        figure.play(step.moves, step.chapter, step.part) {
            if (isFinishing) return@play
            btnSkip.visibility = View.GONE
            if (last) {
                finishGame()
            } else {
                if (index + 1 == PrayerGame.NAMAZ_START) {
                    // Abdest bitti: kayıt başa döner ve "Namaza geçelim mi?" diye sorulur
                    Store.setPrayerStep(this, PrayerChapter.ABDEST, PrayerGame.chapterStart(PrayerChapter.ABDEST))
                    bar.progress = bar.max
                    tvInfo.text = tvInfo.text.toString() + getString(R.string.learn_ui_101)
                    btnNext.text = getString(R.string.learn_ui_102)
                    btnLater.visibility = View.VISIBLE
                } else {
                    Store.setPrayerStep(this, step.chapter, index + 1)
                }
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
    private fun showRecite(original: Recitation, n: Int) {
        val r = learning.recitation(original)
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
        span(getString(R.string.learn_ui_103, r.meaning), ForegroundColorSpan(getColor(R.color.muted)), RelativeSizeSpan(0.8f))
        if (r.source.isNotEmpty()) span(getString(R.string.learn_ui_104, r.source), ForegroundColorSpan(getColor(R.color.muted)), RelativeSizeSpan(0.75f))
        tvRecite.text = sb
        tvRecite.contentDescription = getString(R.string.learn_ui_105, r.title, r.text, r.meaning)
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
        Store.setPrayerStep(this, PrayerChapter.NAMAZ, PrayerGame.NAMAZ_START)
        Store.addPrayerWin(this)
        bar.progress = bar.max
        figure.celebrate()
        questionBox.visibility = View.GONE
        tvStepTitle.visibility = View.INVISIBLE
        btnSpeak.visibility = View.GONE
        val name = if (girl) "Zeynep" else "Ahmet"
        findViewById<TextView>(R.id.tvFinishTitle).text = getString(R.string.learn_ui_106)
        findViewById<TextView>(R.id.tvFinishText).text =
            getString(R.string.learn_ui_107, name) +
            getString(R.string.learn_ui_108)
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
        val step = learning.step(index)
        val text = if (busy) {
            QuizSpeech.clean(tvStepTitle.text.toString() + ". " + step.info)
        } else {
            val shown = (0 until options.childCount).map { (options.getChildAt(it).tag as? String).orEmpty() }
            learning.question(step.question, shown)
        }
        speak(text)
    }

    /** Atlanan okuma, motor geç açılsa bile yeniden başlamamalı. */
    private fun stopSpeech() {
        pendingSpeech = null
        tts?.stop()
    }

    private fun speak(text: String) {
        voice.stop() // soru okunurken dua kaydı üstüne binmesin
        val engine = tts
        if (engine != null && ttsReady) {
            pendingSpeech = null
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "prayer")
            return
        }
        pendingSpeech = text
        if (engine != null) return // motor hâlâ açılıyor; hazır olunca okunur
        tts = TextToSpeech(this) init@{ status ->
            val t = tts ?: return@init
            val lang = if (status == TextToSpeech.SUCCESS) t.setLanguage(Lang.locale(this)) else TextToSpeech.LANG_NOT_SUPPORTED
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(
                    this,
                    getString(R.string.learn_ui_035),
                    Toast.LENGTH_LONG,
                ).show()
                pendingSpeech = null
                ttsReady = false
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
