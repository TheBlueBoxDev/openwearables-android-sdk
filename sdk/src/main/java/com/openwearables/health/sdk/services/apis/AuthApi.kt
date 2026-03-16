package com.openwearables.health.sdk.services.apis

import com.openwearables.health.sdk.data.requests.RefreshTokenRequest
import com.openwearables.health.sdk.data.responses.RefreshResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("/token/refresh")
    suspend fun refreshTokens(
        @Body request: RefreshTokenRequest
    ): Response<RefreshResponse>
}
