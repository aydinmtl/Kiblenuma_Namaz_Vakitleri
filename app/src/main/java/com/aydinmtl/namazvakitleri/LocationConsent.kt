package com.aydinmtl.namazvakitleri

import android.Manifest
import android.app.Activity
import android.app.AlertDialog

/** Konum izni istemeden önce NEDEN istendiğini açıklayan diyalog. */
object LocationConsent {
    const val REQUEST_CODE = 42

    /**
     * [onReady]: izin zaten varsa hemen çağrılır. Yoksa izin penceresi açılır;
     * sonucu Activity'nin onRequestPermissionsResult metoduna [REQUEST_CODE] ile gelir.
     */
    fun show(activity: Activity, onReady: () -> Unit, onManual: () -> Unit) {
        Store.setLocationIntroShown(activity)
        AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(R.string.consent_title)
            .setMessage(R.string.consent_message)
            .setCancelable(false)
            .setPositiveButton(R.string.consent_yes) { _, _ ->
                Store.setAutoLocation(activity, true)
                if (LocationHelper.hasPermission(activity)) {
                    onReady()
                } else {
                    activity.requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQUEST_CODE)
                }
            }
            .setNegativeButton(R.string.consent_no) { _, _ ->
                Store.setAutoLocation(activity, false)
                onManual()
            }
            .show()
    }
}
