package com.openwearables.health.sdk.data.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceSourceData(
    @SerialName("deviceUdiDeviceIdentifier")
    val deviceId: String
)

@Serializable
data class HealthDataItem(
    @SerialName("uuid")
    val uuid: String,
    @SerialName("type")
    val type: String,
    @SerialName("value")
    val value: Double?,
    @SerialName("unit")
    val unit: String?,
    @SerialName("start_date")
    val startDate: String,
    @SerialName("end_date")
    val endDate: String,
    @SerialName("source")
    val source: DeviceSourceData,
    @SerialName("record_metadata")
    val metadata: Map<String, String>
)