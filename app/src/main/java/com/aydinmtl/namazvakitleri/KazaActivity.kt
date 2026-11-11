package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Kaza namazı borcu: her namaz için sayaç. */
class KazaActivity : Activity() {

    private val names = listOf("Sabah", "Öğle", "İkindi", "Akşam", "Yatsı", "Vitir")
    private val countViews = ArrayList<TextView>()
    private lateinit var tvTotal: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kaza)
        Ui.fitSystemBars(this, findViewById(R.id.root))
        tvTotal = findViewById(R.id.tvTotal)

        val box = findViewById<LinearLayout>(R.id.kazaRows)
        for (i in names.indices) {
            val row = layoutInflater.inflate(R.layout.item_kaza, box, false)
            row.findViewById<TextView>(R.id.name).text = names[i]
            val count = row.findViewById<TextView>(R.id.count)
            row.findViewById<Button>(R.id.minus).setOnClickListener { change(i, -1) }
            row.findViewById<Button>(R.id.plus).setOnClickListener { change(i, +1) }
            count.setOnClickListener { askNumber(i) }
            box.addView(row)
            countViews.add(count)
        }
        refresh()
    }

    private fun change(i: Int, delta: Int) {
        Store.setKaza(this, i, Store.kaza(this, i) + delta)
        refresh()
    }

    private fun askNumber(i: Int) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setText(Store.kaza(this, i).toString())
        input.setSelectAllOnFocus(true)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("${names[i]} kazası")
            .setView(input)
            .setPositiveButton("Kaydet") { _, _ ->
                // Çok büyük sayı yazılırsa eskiden sessizce 0 oluyordu; artık üst sınırda durur.
                // (Bir vakit için 100.000 kaza ≈ 270 yıl; gerçekte bundan fazlası olmaz.)
                // Boş bırakılırsa 0. Yalnızca rakam girilebildiği için sayıya çevrilemeyen bir
                // yazı ancak aşırı uzun bir sayı olabilir; o da üst sınır sayılır.
                val t = input.text.toString().trim()
                val n = if (t.isEmpty()) 0L else t.toLongOrNull() ?: Long.MAX_VALUE
                Store.setKaza(this, i, n.coerceIn(0L, KAZA_MAX.toLong()).toInt())
                refresh()
            }
            .setNegativeButton("Vazgeç", null)
            .show()
    }

    private fun refresh() {
        var total = 0
        for (i in names.indices) {
            val n = Store.kaza(this, i)
            total += n
            countViews[i].text = n.toString()
        }
        tvTotal.text = if (total == 0) "Kaza borcu yok" else "Toplam: $total vakit"
    }

    companion object {
        const val KAZA_MAX = 100_000
    }
}
