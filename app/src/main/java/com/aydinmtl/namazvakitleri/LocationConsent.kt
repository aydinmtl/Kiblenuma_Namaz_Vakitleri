package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.Activity
import android.app.AlertDialog

/** Konum izni istemeden önce NEDEN istendiğini açıklayan diyalog. */
object LocationConsent {
    const val REQUEST_CODE = 42

    private const val MESSAGE =
        "Konumun şunlar için kullanılır:\n\n" +
            "• Bulunduğun şehri bulup oranın Diyanet vakitlerini göstermek\n" +
            "• Seyahat ettiğinde vakitleri ve ekrandaki şehir/ülke adını kendiliğinden değiştirmek\n" +
            "• Kıble yönünü hesaplamak\n\n" +
            "Yalnızca yaklaşık konum (şehir düzeyi) istenir. Konum, uygulamayı açtığında kontrol edilir ve " +
            "telefonunda saklanır; uygulamanın kendine ait bir sunucusu yoktur.\n\n" +
            "Şehir adını bulmak için önce telefonunun kendi konum hizmetine sorulur. O çalışmazsa koordinat, " +
            "kimlik bilgisi olmadan OpenStreetMap'e gönderilir. \"Yakındaki Camiler\" ekranı da camileri " +
            "bulmak için koordinatı OpenStreetMap'e gönderir.\n\n" +
            "İstersen izin vermeden şehrini elle de seçebilirsin."

    /**
     * [onReady]: izin zaten varsa hemen çağrılır. Yoksa izin penceresi açılır;
     * sonucu Activity'nin onRequestPermissionsResult metoduna [REQUEST_CODE] ile gelir.
     */
    fun show(activity: Activity, onReady: () -> Unit, onManual: () -> Unit) {
        Store.setLocationIntroShown(activity)
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Konum izni neden isteniyor?")
            .setMessage(MESSAGE)
            .setCancelable(false)
            .setPositiveButton("Konumumu kullan") { _, _ ->
                Store.setAutoLocation(activity, true)
                if (LocationHelper.hasPermission(activity)) {
                    onReady()
                } else {
                    activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_CODE)
                }
            }
            .setNegativeButton("Elle seçeceğim") { _, _ ->
                Store.setAutoLocation(activity, false)
                onManual()
            }
            .show()
    }
}
