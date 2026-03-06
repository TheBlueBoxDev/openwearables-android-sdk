package com.openwearables.healthsdk.data.requests

import kotlinx.serialization.SerialName

data class RefreshTokenRequest(
    @SerialName("refresh_token") val oldToken: String
)
