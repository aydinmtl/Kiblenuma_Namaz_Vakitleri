package com.aydinmtl.namazvakitleri

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
class HolyDaysActivity : LangActivity() {

    private val dateFormat by lazy { DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", Lang.locale(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_list)
        Ui.fitSystemBars(this, findViewById(R.id.root))

        Times.days(this) // Hicri tarihi Diyanet takvimiyle hizalar
        val today = LocalDate.now()
        val items = Hijri.holyDays(today)

        findViewById<TextView>(R.id.tvTitle).text = getString(R.string.hd_title)
        findViewById<TextView>(R.id.tvSubtitle).text = getString(R.string.hd_info, Lang.hijriDate(this, Hijri.of(today)))

        findViewById<ListView>(R.id.list).adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int): Any = items[position]
            override fun getItemId(position: Int) = position.toLong()

            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val v = convertView ?: layoutInflater.inflate(R.layout.item_two_line, parent, false)
                val h = items[position]
                val name = Lang.holyDayName(this@HolyDaysActivity, h)
                v.findViewById<TextView>(R.id.line1).text = if (h.night) "🌙 $name" else name
                v.findViewById<TextView>(R.id.line2).text = h.date.format(dateFormat) + if (h.night) getString(R.string.hd_night) else ""
                val days = ChronoUnit.DAYS.between(today, h.date)
                v.findViewById<TextView>(R.id.right).text = when (days) {
                    0L -> getString(R.string.hd_today)
                    1L -> getString(R.string.hd_tomorrow)
                    else -> resources.getQuantityString(R.plurals.hd_in_days, days.toInt(), days.toInt())
                }
                return v
            }
        }
    }
}
