package com.openwearables.healthsdk.services.apis

import com.openwearables.healthsdk.data.requests.RefreshTokenRequest
import com.openwearables.healthsdk.data.responses.RefreshResponse
import kotlinx.serialization.SerialName
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiAuth {

    @POST("/token/refresh")
    suspend fun refreshTokens(
        @Body request: RefreshTokenRequest
    ): RefreshResponse
}
