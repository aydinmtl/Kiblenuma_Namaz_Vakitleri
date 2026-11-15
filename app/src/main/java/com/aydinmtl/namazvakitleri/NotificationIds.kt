package com.aydinmtl.namazvakitleri

/** Uygulama genelinde tek kimlik alanı: kanallar bildirim kimliklerini birbirinden ayırmaz. */
object NotificationIds {
    const val ADHAN = 300
    const val DAILY = 400
    fun prayer(index: Int): Int = 100 + index
    fun reminder(index: Int): Int = 200 + index
}
