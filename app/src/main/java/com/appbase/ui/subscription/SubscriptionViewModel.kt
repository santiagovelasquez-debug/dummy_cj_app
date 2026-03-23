package com.appbase.ui.subscription

import android.content.Context
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.appbase.data.model.AccessReason
import com.appbase.data.model.VerifyAccessResponse
import com.appbase.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class SubscriptionUiState {
    object Idle : SubscriptionUiState()
    object Loading : SubscriptionUiState()

    // accessGranted=true → launch external app
    data class AccessGranted(
        val plan: String,
        val remainingDays: Int,
        val message: String
    ) : SubscriptionUiState()

    // accessGranted=false, reason="no_subscription" → show subscribe button
    object NoSubscription : SubscriptionUiState()

    // accessGranted=false, reason="expired" → show renew button
    data class Expired(val expiredAt: String?) : SubscriptionUiState()

    // Network error or HTTP 500
    data class Error(val message: String) : SubscriptionUiState()
}

class SubscriptionViewModel(
    private val repository: SubscriptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SubscriptionUiState>(SubscriptionUiState.Idle)
    val uiState: StateFlow<SubscriptionUiState> = _uiState

    /**
     * Call this when the user taps "Subscription" in SelectionActivity.
     * Also call on onResume so expiration is always caught server-side.
     */
    fun verifyAccess(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = SubscriptionUiState.Loading
            repository.verifyAccess(deviceId)
                .onSuccess { _uiState.value = mapResponse(it) }
                .onFailure {
                    _uiState.value = SubscriptionUiState.Error(
                        it.message ?: "Error de conexión"
                    )
                }
        }
    }

    /**
     * Opens the payment/renewal web flow in a Chrome Custom Tab.
     * Call when user taps "Subscribe" or "Renew".
     */
    fun openPaymentFlow(context: Context, deviceId: String) {
        repository.openPaymentFlow(
            context     = context,
            deviceId    = deviceId,
            deviceName  = Build.MODEL,
            appVersion  = getAppVersion(context)
        )
    }

    fun resetState() {
        _uiState.value = SubscriptionUiState.Idle
    }

    private fun mapResponse(response: VerifyAccessResponse): SubscriptionUiState {
        return if (response.accessGranted) {
            SubscriptionUiState.AccessGranted(
                plan         = response.plan ?: "",
                remainingDays = response.remainingDays ?: 0,
                message      = response.message
            )
        } else {
            when (AccessReason.from(response.reason)) {
                AccessReason.EXPIRED         -> SubscriptionUiState.Expired(response.expiredAt)
                AccessReason.NO_SUBSCRIPTION -> SubscriptionUiState.NoSubscription
                AccessReason.UNKNOWN         -> SubscriptionUiState.Error(response.message)
            }
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) { "unknown" }
    }
}
