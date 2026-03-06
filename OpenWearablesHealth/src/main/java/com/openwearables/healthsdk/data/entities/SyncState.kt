package com.openwearables.healthsdk.data.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Date

@Serializable
data class TypeSyncProgress(
    @SerialName("type_identifier")
    val typeIdentifier: String,
    @SerialName("sent_count")
    var sentCount: Int,
    @SerialName("is_complete")
    var isComplete: Boolean
)

@Serializable
data class SyncState(
    @SerialName("user_key")
    val userKey: String,
    @SerialName("full_export")
    val fullExport: Boolean,
    @SerialName("created_at")
    val createdAt: Date,
    @SerialName("type_progress")
    val typeProgress: MutableMap<String, TypeSyncProgress>,
    @SerialName("total_sent_count")
    var totalSentCount: Int = 0,
    @SerialName("completed_types")
    val completedTypes: MutableSet<String>,
    @SerialName("current_type_index")
    var currentTypeIndex: Int
) {
    val hasProgress: Boolean
        get() {
            return totalSentCount > 0 || !completedTypes.isEmpty()
        }
}
