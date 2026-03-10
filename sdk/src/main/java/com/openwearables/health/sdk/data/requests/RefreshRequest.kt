package com.openwearables.health.sdk.data.requests

import kotlinx.serialization.SerialName

data class RefreshTokenRequest(
    @SerialName("refresh_token") val oldToken: String
)
