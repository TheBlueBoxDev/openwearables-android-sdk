package com.openwearables.health.sdk.data.entities

import kotlinx.serialization.Serializable


// ---------------------------------------------------------------------------
// Internal raw data models (Samsung-specific, not exposed via interface)
// ---------------------------------------------------------------------------

@Serializable
data class HealthDataRecord(
    val uid: String,
    val dataType: String,
    val startTime: Long,
    val endTime: Long?,
    val zoneOffset: String?,
    val dataSource: RawDataSource?,
    val device: DeviceInfo,
    val fields: Map<String, Any?>
)

@Serializable
data class RawDataSource(val appId: String, val deviceId: String?)

@Serializable
data class DeviceInfo(
    val deviceId: String?,
    val manufacturer: String,
    val model: String,
    val name: String,
    val brand: String,
    val product: String,
    val osType: String,
    val osVersion: String,
    val sdkVersion: Int,
    val deviceType: String?,
    val isSourceDevice: Boolean = false
)
