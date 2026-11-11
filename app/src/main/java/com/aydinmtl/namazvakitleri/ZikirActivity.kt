package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.View
import android.widget.TextView

/** Basit zikirmatik: dokun = +1, hedefe ulaşınca uzun titreşim ve yeni tur. */
class ZikirActivity : Activity() {

    private val targets = intArrayOf(33, 99, 100, 500, 1000, 0) // 0 = sınırsız

    private var count = 0
    private var target = 33
    private var rounds = 0

    private lateinit var tvCount: TextView
    private lateinit var tvRounds: TextView
    private lateinit var rowTarget: TextView
    private var vibrator: Vibrator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_zikir)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        tvCount = findViewById(R.id.tvCount)
        tvRounds = findViewById(R.id.tvRounds)
        rowTarget = findViewById(R.id.rowTarget)
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

        count = Store.zikirCount(this)
        target = Store.zikirTarget(this)
        rounds = Store.zikirRounds(this)

        findViewById<View>(R.id.tapArea).setOnClickListener { increment() }
        findViewById<View>(R.id.btnUndo).setOnClickListener {
            if (count > 0) count--
            save()
        }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Sayaç sıfırlansın mı?")
                .setPositiveButton("Sıfırla") { _, _ ->
                    count = 0
                    rounds = 0
                    save()
                }
                .setNegativeButton("Vazgeç", null)
                .show()
        }
        rowTarget.setOnClickListener { chooseTarget() }
        show()
    }

    private fun increment() {
        count++
        if (target > 0 && count >= target) {
            rounds++
            count = 0
            vibrate(400)
        } else {
            vibrate(25)
        }
        save()
    }

    private fun vibrate(ms: Long) {
        try {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (e: Exception) {
        }
    }

    private fun chooseTarget() {
        val labels = targets.map { if (it == 0) "Sınırsız" else it.toString() }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Hedef")
            .setSingleChoiceItems(labels, targets.indexOf(target).coerceAtLeast(0)) { d, which ->
                d.dismiss()
                target = targets[which]
                count = 0
                rounds = 0
                save()
            }
            .show()
    }

    private fun save() {
        Store.saveZikir(this, count, target, rounds)
        show()
    }

    private fun show() {
        tvCount.text = count.toString()
        rowTarget.text = "Hedef: " + (if (target == 0) "Sınırsız" else "$target") + "  ›"
        tvRounds.text = if (target == 0) "" else "Tamamlanan tur: $rounds  (toplam ${rounds * target + count})"
    }

    /** Ses açma tuşu da sayar (ekrana bakmadan çekmek için). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            increment()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
