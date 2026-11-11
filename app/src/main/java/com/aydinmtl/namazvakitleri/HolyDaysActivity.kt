package com.aydinmtl.namazvakitleri

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Önümüzdeki bir yılın dini günleri ve kandilleri. */
class HolyDaysActivity : Activity() {

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", TR)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        Times.days(this) // Hicri tarihi Diyanet takvimiyle hizalar
        val today = LocalDate.now()
        val items = Hijri.holyDays(today)

        findViewById<TextView>(R.id.tvTitle).text = "Dini Günler"
        findViewById<TextView>(R.id.tvSubtitle).text =
            "Bugün: ${Hijri.format(today)}\n" +
            "Hicri takvimle hesaplanır ve Diyanet takvimine göre ayarlanır. Resmi ilan edilen tarihten 1 gün sapabilir. " +
            "Kandiller, tarihi verilen günü sonraki güne bağlayan gecedir."

        findViewById<ListView>(R.id.list).adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_two_line, parent, false)
                val h = items[position]
                v.findViewById<TextView>(R.id.line1).text = if (h.night) "🌙 ${h.name}" else h.name
                v.findViewById<TextView>(R.id.line2).text = h.date.format(dateFormat) + if (h.night) " (gecesi)" else ""
                val days = ChronoUnit.DAYS.between(today, h.date)
                v.findViewById<TextView>(R.id.right).text = when (days) {
                    0L -> "Bugün"
                    1L -> "Yarın"
                    else -> "$days gün"
                }
                return v
            }
        }
    }
}
