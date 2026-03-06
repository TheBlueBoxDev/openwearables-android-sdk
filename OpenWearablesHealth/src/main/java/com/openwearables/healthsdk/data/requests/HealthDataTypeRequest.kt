package com.openwearables.healthsdk.data.requests

import com.openwearables.healthsdk.data.entities.HealthDataItem
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