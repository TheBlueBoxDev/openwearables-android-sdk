package com.openwearables.health.sdk.services.apis

import com.openwearables.health.sdk.data.requests.RefreshTokenRequest
import com.openwearables.health.sdk.data.responses.RefreshResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiAuth {

    @POST("/token/refresh")
    suspend fun refreshTokens(
        @Body request: RefreshTokenRequest
    ): RefreshResponse
}
