package com.aydinmtl.namazvakitleri

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Kayıtlı ~30 günün vakit tablosu. Bugünün satırı vurgulanır. */
class ImsakiyeActivity : LangActivity() {

    private val cellIds = intArrayOf(R.id.c0, R.id.c1, R.id.c2, R.id.c3, R.id.c4, R.id.c5)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM EEE", TR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_imsakiye)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        findViewById<TextView>(R.id.tvTitle).text = "İmsakiye · ${Store.placeName(this)}"

        val days = Times.days(this)
        val today = LocalDate.now()
        val highlight = getColor(R.color.primary)
        val list = findViewById<ListView>(R.id.list)

        list.adapter = object : BaseAdapter() {
            override fun getCount() = days.size
            override fun getItem(position: Int): Any = days[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_imsakiye, parent, false)
                val d = days[position]
                v.findViewById<TextView>(R.id.c_date).text = d.date.format(dateFormat)
                for (i in 0..5) v.findViewById<TextView>(cellIds[i]).text = d.times[i].format(HHMM)
                v.setBackgroundColor(if (d.date == today) highlight else Color.TRANSPARENT)
                return v
            }
        }

        val todayIndex = days.indexOfFirst { it.date == today }
        if (todayIndex > 0) list.setSelection(todayIndex)
    }
}
