package com.appbase.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for GET /api/verify-access/:deviceId
 *
 * All scenarios return HTTP 200.
 * Always check [accessGranted] first — never trust the device clock.
 */
data class VerifyAccessResponse(

    @SerializedName("accessGranted")
    val accessGranted: Boolean,

    // "no_subscription" or "expired" — only present when accessGranted=false
    @SerializedName("reason")
    val reason: String?,

    // Human-readable message — always present
    @SerializedName("message")
    val message: String,

    // Plan ID: "48h", "1month", "6months" — only when accessGranted=true
    @SerializedName("plan")
    val plan: String?,

    // Expiration date in ISO 8601 UTC — only when accessGranted=true
    @SerializedName("endDate")
    val endDate: String?,

    // Total remaining hours — only when accessGranted=true
    @SerializedName("remainingHours")
    val remainingHours: Int?,

    // Full remaining days — only when accessGranted=true
    @SerializedName("remainingDays")
    val remainingDays: Int?,

    // Signed JWT valid until endDate — only when accessGranted=true
    @SerializedName("token")
    val token: String?,

    // When the subscription expired — only when reason="expired"
    @SerializedName("expiredAt")
    val expiredAt: String?
)

enum class AccessReason {
    NO_SUBSCRIPTION,
    EXPIRED,
    UNKNOWN;

    companion object {
        fun from(value: String?): AccessReason = when (value) {
            "no_subscription" -> NO_SUBSCRIPTION
            "expired"         -> EXPIRED
            else              -> UNKNOWN
        }
    }
}
