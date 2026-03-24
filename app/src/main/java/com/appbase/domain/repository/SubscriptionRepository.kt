package com.appbase.domain.repository

import android.content.Context
import com.appbase.data.model.VerifyAccessResponse

interface SubscriptionRepository {

    /** GET /api/verify-access/:deviceId */
    suspend fun verifyAccess(deviceId: String): Result<VerifyAccessResponse>

    /**
     * Opens the payment/renewal flow inside the app using a WebView.
     * Launches PaymentWebViewActivity — user stays inside the app.
     * On payment completion the server redirects to a known URL and
     * PaymentWebViewActivity intercepts it, then returns to SelectionActivity.
     *
     * @param context     Activity context required to launch the Activity
     * @param deviceId    Unique device identifier
     * @param deviceName  e.g. Build.MODEL
     * @param appVersion  e.g. BuildConfig.VERSION_NAME
     */
    fun openPaymentFlow(
        context: Context,
        deviceId: String,
        deviceName: String,
        appVersion: String
    )
}
