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
                Store.setKaza(this, i, input.text.toString().toIntOrNull() ?: 0)
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
}
