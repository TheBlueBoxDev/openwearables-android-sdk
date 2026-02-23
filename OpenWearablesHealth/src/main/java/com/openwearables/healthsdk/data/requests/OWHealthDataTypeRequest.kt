package com.openwearables.healthsdk.data.requests

import com.google.gson.annotations.SerializedName

data class DeviceSourceData(
    @SerializedName("deviceUdiDeviceIdentifier") val deviceId: String
)

data class HealthItemRequest(
    @SerializedName("uuid")
    val uuid: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("value")
    val value: Double?,
    @SerializedName("unit")
    val unit: String?,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("endDate") val endDate: String,
    @SerializedName("source") val source: DeviceSourceData,
    @SerializedName("recordMetadata") val metadata: Map<String, String>
)

data class OWHealthDataTypeRequest(
    @SerializedName("data")
    val data: Map<String, Array<HealthItemRequest>>
)