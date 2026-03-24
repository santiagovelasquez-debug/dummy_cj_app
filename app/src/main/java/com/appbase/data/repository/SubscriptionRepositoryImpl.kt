package com.appbase.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.appbase.data.model.VerifyAccessResponse
import com.appbase.data.remote.CJ9ApiService
import com.appbase.domain.repository.SubscriptionRepository

class SubscriptionRepositoryImpl(
    private val api: CJ9ApiService
) : SubscriptionRepository {

    override suspend fun verifyAccess(deviceId: String): Result<VerifyAccessResponse> {
        return try {
            val response = api.verifyAccess(deviceId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Error del servidor — HTTP ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Per API docs: this endpoint does an HTTP redirect.
     * NEVER call it with Retrofit — always open in Chrome Custom Tab.
     */
    override fun openPaymentFlow(
        context: Context,
        deviceId: String,
        deviceName: String,
        appVersion: String
    ) {
        val url = Uri.parse("${BASE_URL}api/prepare-device")
            .buildUpon()
            .appendQueryParameter("deviceId", deviceId)
            .appendQueryParameter("deviceName", deviceName)
            .appendQueryParameter("appVersion", appVersion)
            .build()

        // Abrir en Edge específicamente
        val intent = Intent(Intent.ACTION_VIEW, url).apply {
            setPackage("com.microsoft.emmx") // Edge
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback si Edge no está
            val fallback = Intent(Intent.ACTION_VIEW, url)
            context.startActivity(fallback)
        }
    }

    companion object {
        const val BASE_URL =
            "https://payments-subscriptions-dot-injectronic-sistemas.uc.r.appspot.com/"
    }
}
