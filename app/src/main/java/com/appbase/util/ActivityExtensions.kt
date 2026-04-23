// File: app/src/main/java/com/appbase/util/ActivityExtensions.kt
package com.appbase.util

import android.app.Activity
import android.app.AlertDialog

/**
 * Launches an external application by package name.
 * Shows an error dialog if the app is not installed.
 *
 * @param packageName The package name of the app to launch
 * @param onAppNotFound Optional callback when app is not found (after dialog dismissed)
 * @param finishAfterLaunch Whether to finish the current activity after launching
 */
fun Activity.launchExternalApp(
    packageName: String,
    finishAfterLaunch: Boolean = false,
    onAppNotFound: (() -> Unit)? = null
) {
    val intent = packageManager.getLaunchIntentForPackage(packageName)
    if (intent != null) {
        startActivity(intent)
        if (finishAfterLaunch) {
            finish()
        }
    } else {
        AlertDialog.Builder(this)
            .setTitle("App No Encontrada")
            .setMessage("La aplicación requerida no está instalada en este dispositivo.")
            .setPositiveButton("OK") { _, _ ->
                onAppNotFound?.invoke()
            }
            .setCancelable(false)
            .show()
    }
}