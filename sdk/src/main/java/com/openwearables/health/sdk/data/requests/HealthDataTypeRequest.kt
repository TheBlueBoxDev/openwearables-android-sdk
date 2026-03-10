package com.openwearables.health.sdk.data.requests

import com.openwearables.health.sdk.data.entities.HealthDataItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthDataTypeRequest(
    @SerialName("provider")
    val provider: String = "android",
    @SerialName("sdkVersion")
    val sdkVersion: String,
    @SerialName("syncTimestamp")
    val syncTimestamp: String,
    @SerialName("data")
    val data: Map<String, Array<HealthDataItem>>
)