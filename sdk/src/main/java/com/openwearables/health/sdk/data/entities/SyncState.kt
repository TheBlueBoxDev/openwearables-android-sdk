package com.openwearables.health.sdk.data.entities

import kotlinx.serialization.Serializable

@Serializable
data class TypeSyncProgress(
    val typeIdentifier: String,
    var sentCount: Int = 0,
    var isComplete: Boolean = false,
    var pendingAnchorTimestamp: Long? = null
)

@Serializable
data class SyncState(
    val userKey: String,
    val fullExport: Boolean,
    val createdAt: Long,
    val typeProgress: MutableMap<String, TypeSyncProgress> = mutableMapOf(),
    var totalSentCount: Int = 0,
    val completedTypes: MutableSet<String> = mutableSetOf(),
    var currentTypeIndex: Int = 0
) {
    val hasProgress: Boolean
        get() {
            return totalSentCount > 0 || !completedTypes.isEmpty()
        }
}
