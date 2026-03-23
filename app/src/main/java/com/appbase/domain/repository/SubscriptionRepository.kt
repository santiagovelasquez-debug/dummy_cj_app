package com.appbase.domain.repository

import android.content.Context
import com.appbase.data.model.VerifyAccessResponse

interface SubscriptionRepository {

    /** GET /api/verify-access/:deviceId */
    suspend fun verifyAccess(deviceId: String): Result<VerifyAccessResponse>

    /**
     * Opens /api/prepare-device in a Chrome Custom Tab.
     * This is the full payment/renewal web flow.
     */
    fun openPaymentFlow(
        context: Context,
        deviceId: String,
        deviceName: String,
        appVersion: String
    )
}
