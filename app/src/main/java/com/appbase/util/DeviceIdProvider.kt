package com.appbase.util

import android.content.Context
import android.provider.Settings
import java.util.UUID

/**
 * Provides a stable unique device ID for subscription validation.
 *
 * Strategy:
 *   1. Use ANDROID_ID — stable, no permissions, survives reinstalls
 *   2. Fallback to a UUID in SharedPreferences if ANDROID_ID is unreliable
 *      (known bad values on some manufacturer ROMs / emulators)
 */
object DeviceIdProvider {

    private const val PREFS_NAME = "cj9_device_prefs"
    private const val KEY_FALLBACK_ID = "fallback_device_id"

    private val UNRELIABLE_IDS = setOf(
        "9774d56d682e549c",
        "0000000000000000",
        "unknown"
    )

    fun getDeviceId(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return if (!androidId.isNullOrBlank() && androidId !in UNRELIABLE_IDS) {
            androidId
        } else {
            getFallbackId(context)
        }
    }

    private fun getFallbackId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_FALLBACK_ID, null)
        if (existing != null) return existing

        val newId = UUID.randomUUID().toString().replace("-", "")
        prefs.edit().putString(KEY_FALLBACK_ID, newId).apply()
        return newId
    }
}
