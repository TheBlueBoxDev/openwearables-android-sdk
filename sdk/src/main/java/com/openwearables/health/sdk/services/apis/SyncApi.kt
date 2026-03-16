package com.openwearables.health.sdk.services.apis

import com.openwearables.health.sdk.data.requests.HealthDataTypeRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface SyncApi {

    @POST("/sdk/users/{userId}/sync")
    @Headers(
        "Content-Type: application/json"
    )
    suspend fun syncHealthData(
        @Header("X-Open-Wearables-API-Key") apiKey: String?,
        @Header("Authorization") token: String?,
        @Path("userId") userId: String,
        @Body data: HealthDataTypeRequest
    ): Response<Boolean>
}