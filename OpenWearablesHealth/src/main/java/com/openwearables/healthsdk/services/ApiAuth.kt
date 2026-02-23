package com.openwearables.healthsdk.services

import kotlinx.coroutines.flow.Flow
import retrofit2.http.POST
import retrofit2.http.Path

interface ApiAuth {

    @POST("/api/v1/sdk/users/{userId}/sync/apple")
    suspend fun syncEndpoint(
        @Path("userId") userId: String
    ): Flow<Boolean>
}