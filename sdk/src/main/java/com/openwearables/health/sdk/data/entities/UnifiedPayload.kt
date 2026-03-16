package com.openwearables.health.sdk.data.entities

import kotlinx.serialization.Serializable

/**
 * Unified health data models following the Unified Health Payload specification.
 * All providers (Apple Health, Samsung Health, Health Connect) convert their
 * native data into these structures before syncing.
 */

// ---------------------------------------------------------------------------
// Source
// ---------------------------------------------------------------------------

@Serializable
data class UnifiedSource(
    val appId: String,
    val deviceId: String?,
    val deviceName: String?,
    val deviceManufacturer: String?,
    val deviceModel: String?,
    val deviceType: String?,
    val recordingMethod: String?
)

// ---------------------------------------------------------------------------
// Record (10 keys)
// ---------------------------------------------------------------------------

@Serializable
data class UnifiedRecord(
    val id: String,
    val type: String,
    val startDate: String,
    val endDate: String,
    val zoneOffset: String?,
    val source: UnifiedSource,
    val value: Double,
    val unit: String,
    val parentId: String?,
    val metadata: Map<String, Any?>?
)

// ---------------------------------------------------------------------------
// Workout (15 keys)
// ---------------------------------------------------------------------------

@Serializable
data class UnifiedWorkout(
    val id: String,
    val parentId: String?,
    val type: String,
    val startDate: String,
    val endDate: String,
    val zoneOffset: String?,
    val source: UnifiedSource,
    val title: String?,
    val notes: String?,
    val values: List<Map<String, Any>>?,
    val segments: List<Map<String, Any?>>?,
    val laps: List<Map<String, Any?>>?,
    val route: List<Map<String, Any?>>?,
    val samples: List<Map<String, Any?>>?,
    val metadata: Map<String, Any?>?
)

// ---------------------------------------------------------------------------
// Sleep (9 keys)
// ---------------------------------------------------------------------------

@Serializable
data class UnifiedSleep(
    val id: String,
    val parentId: String?,
    val stage: String,
    val startDate: String,
    val endDate: String,
    val zoneOffset: String?,
    val source: UnifiedSource,
    val values: List<Map<String, Any>>?,
    val metadata: Map<String, Any?>?
)

// ---------------------------------------------------------------------------
// Aggregated read result
// ---------------------------------------------------------------------------

@Serializable
data class UnifiedHealthData(
    val records: List<UnifiedRecord> = emptyList(),
    val workouts: List<UnifiedWorkout> = emptyList(),
    val sleep: List<UnifiedSleep> = emptyList()
) {
    val isEmpty: Boolean
        get() = records.isEmpty() && workouts.isEmpty() && sleep.isEmpty()

    val totalCount: Int
        get() = records.size + workouts.size + sleep.size
}

@Serializable
data class ProviderReadResult(
    val data: UnifiedHealthData,
    val maxTimestamp: Long?,
    val minTimestamp: Long? = null
)

// ---------------------------------------------------------------------------
// Samsung device type → unified device type mapping
// ---------------------------------------------------------------------------

object DeviceTypeMapper {
    fun fromSamsungDeviceType(type: String?): String? = when (type?.uppercase()) {
        "MOBILE" -> "phone"
        "WATCH" -> "watch"
        "RING" -> "ring"
        "BAND" -> "fitness_band"
        "ACCESSORY" -> "unknown"
        else -> null
    }

    fun fromHealthConnectDeviceType(type: Int): String = when (type) {
        0 -> "unknown"
        1 -> "watch"
        2 -> "phone"
        3 -> "scale"
        4 -> "ring"
        5 -> "head_mounted"
        6 -> "fitness_band"
        7 -> "chest_strap"
        8 -> "smart_display"
        else -> "unknown"
    }

    fun fromHealthConnectRecordingMethod(method: Int): String = when (method) {
        1 -> "active"
        2 -> "automatic"
        3 -> "manual"
        else -> "unknown"
    }
}
