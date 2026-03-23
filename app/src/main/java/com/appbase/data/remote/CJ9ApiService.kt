package com.appbase.data.remote

import com.appbase.data.model.VerifyAccessResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface CJ9ApiService {

    /**
     * GET /api/verify-access/{deviceId}
     *
     * Always returns HTTP 200. Check [VerifyAccessResponse.accessGranted]
     * for the actual subscription state.
     *
     * NOTE: /api/prepare-device is NOT here — it's a redirect and must
     * be opened in a Chrome Custom Tab, never called via Retrofit.
     */
    @GET("api/verify-access/{deviceId}")
    suspend fun verifyAccess(
        @Path("deviceId") deviceId: String
    ): Response<VerifyAccessResponse>
}
