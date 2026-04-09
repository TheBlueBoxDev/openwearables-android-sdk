package com.openwearables.health.sdk.data.requests

import com.openwearables.health.sdk.data.SyncDefaults
import com.openwearables.health.sdk.data.entities.UnifiedHealthData
import com.openwearables.health.sdk.data.utlis.UnifiedTimestamp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthDataTypeRequest(
    @SerialName("provider")
    val provider: String = "google",
    @SerialName("sdkVersion")
    val sdkVersion: String = SyncDefaults.SDK_VERSION,
    @SerialName("syncTimestamp")
    val syncTimestamp: String = UnifiedTimestamp.fromEpochMs(System.currentTimeMillis()),
    @SerialName("data")
    val data: UnifiedHealthData
)