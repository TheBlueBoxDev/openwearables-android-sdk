package com.openwearables.health.sdk.data.requests

import com.openwearables.health.sdk.data.entities.UnifiedHealthData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthDataTypeRequest(
    @SerialName("provider")
    val provider: String = "google",
    @SerialName("sdkVersion")
    val sdkVersion: String,
    @SerialName("syncTimestamp")
    val syncTimestamp: String,
    @SerialName("data")
    val data: UnifiedHealthData
)