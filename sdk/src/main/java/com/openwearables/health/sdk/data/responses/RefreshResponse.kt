package com.openwearables.health.sdk.data.responses

import kotlinx.serialization.SerialName

data class RefreshResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String
)
