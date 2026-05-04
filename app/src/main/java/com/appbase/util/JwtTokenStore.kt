package com.appbase.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import org.json.JSONObject

/**
 * Almacena y valida localmente el JWT emitido por el servidor,
 * para permitir validación de licencia cuando NO hay conexión a internet.
 *
 * ⚠️ Esta versión NO verifica criptográficamente la firma del JWT.
 *    Solo decodifica el payload (Base64URL) y revisa la expiración.
 *    Además detecta manipulación del reloj del dispositivo comparando
 *    contra la marca de tiempo más alta vista hasta el momento.
 *
 *    Úsala como fallback offline de un token obtenido por HTTPS.
 *    Si necesitas evitar modificación del token, usa la versión con
 *    verificación de firma (RSA256 + clave pública del servidor).
 */
object JwtTokenStore {

    private const val PREFS_NAME = "cj9_license_prefs"

    private const val KEY_TOKEN = "jwt_token"
    private const val KEY_DEVICE_ID = "jwt_device_id"
    private const val KEY_PLAN = "jwt_plan"

    /**
     * Marca de tiempo (epoch, segundos) más alta que hemos observado.
     * Se actualiza al guardar el token y en cada validación offline
     * exitosa. Si `System.currentTimeMillis()` queda por debajo de
     * este valor, asumimos que el usuario retrocedió el reloj.
     */
    private const val KEY_MAX_SEEN_TIME = "jwt_max_seen_time"

    data class OfflineValidation(
        val valid: Boolean,
        val deviceId: String?,
        val plan: String?,
        val remainingDays: Int,
        val expiredAt: String?,
        val reason: String? = null
    )

    // ─── Persistencia ─────────────────────────────────────────────────────────

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(context: Context, token: String, deviceId: String, plan: String?) {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val p = prefs(context)
        val previousMax = p.getLong(KEY_MAX_SEEN_TIME, 0L)
        p.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_PLAN, plan)
            .putLong(KEY_MAX_SEEN_TIME, maxOf(previousMax, nowSeconds))
            .apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun getStoredDeviceId(context: Context): String? =
        prefs(context).getString(KEY_DEVICE_ID, null)

    // ─── Validación offline ───────────────────────────────────────────────────

    /**
     * Valida el JWT almacenado:
     *   1. Debe existir un token para el deviceId solicitado.
     *   2. El reloj del dispositivo no puede ser anterior a la última
     *      marca de tiempo conocida (anti-rollback).
     *   3. El claim `exp` del payload no debe haber vencido.
     *
     * Si la validación es exitosa se actualiza `KEY_MAX_SEEN_TIME`
     * con la hora actual (así el usuario no puede retroceder el reloj
     * después de una comprobación válida).
     */
    fun validateOffline(context: Context, deviceId: String): OfflineValidation {
        val p = prefs(context)

        val token = p.getString(KEY_TOKEN, null)
            ?: return OfflineValidation(false, null, null, 0, null, "no_token")
        val storedDeviceId = p.getString(KEY_DEVICE_ID, null)
        val storedPlan = p.getString(KEY_PLAN, null)

        // 1) El token debe pertenecer al deviceId solicitado
        if (storedDeviceId == null || !storedDeviceId.equals(deviceId, ignoreCase = true)) {
            return OfflineValidation(false, storedDeviceId, storedPlan, 0, null, "device_mismatch")
        }

        // 2) Decodificar payload
        val payload = decodePayload(token)
            ?: return OfflineValidation(false, storedDeviceId, storedPlan, 0, null, "invalid_token")

        val expSeconds = payload.optLong("exp", -1L)
        if (expSeconds <= 0L) {
            return OfflineValidation(false, storedDeviceId, storedPlan, 0, null, "no_exp_claim")
        }

        // 3) Anti-rollback del reloj del dispositivo
        val nowSeconds = System.currentTimeMillis() / 1000L
        val maxSeen = p.getLong(KEY_MAX_SEEN_TIME, 0L)
        if (nowSeconds < maxSeen) {
            // El reloj retrocedió: rechazamos la validación offline.
            return OfflineValidation(
                valid = false,
                deviceId = storedDeviceId,
                plan = storedPlan,
                remainingDays = 0,
                expiredAt = isoUtc(expSeconds * 1000L),
                reason = "clock_tampered"
            )
        }

        // 4) Expiración
        val remainingSeconds = expSeconds - nowSeconds
        if (remainingSeconds <= 0L) {
            return OfflineValidation(
                valid = false,
                deviceId = storedDeviceId,
                plan = storedPlan,
                remainingDays = 0,
                expiredAt = isoUtc(expSeconds * 1000L),
                reason = "expired"
            )
        }

        // 5) Todo correcto → avanzamos la marca de tiempo mínima
        p.edit().putLong(KEY_MAX_SEEN_TIME, nowSeconds).apply()

        val remainingDays = (remainingSeconds / 86_400L).toInt().coerceAtLeast(0)
        return OfflineValidation(
            valid = true,
            deviceId = storedDeviceId,
            plan = storedPlan,
            remainingDays = remainingDays,
            expiredAt = isoUtc(expSeconds * 1000L)
        )
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Decodifica la sección central (payload) de un JWT `header.payload.signature`.
     */
    private fun decodePayload(token: String): JSONObject? = try {
        val parts = token.split(".")
        if (parts.size < 2) null
        else {
            val payloadBytes = Base64.decode(
                parts[1],
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            JSONObject(String(payloadBytes, Charsets.UTF_8))
        }
    } catch (_: Exception) {
        null
    }

    private fun isoUtc(millis: Long): String {
        val fmt = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            java.util.Locale.US
        )
        fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return fmt.format(java.util.Date(millis))
    }
}