package com.openwearables.healthsdk.data.requests

import com.google.gson.annotations.SerializedName
import java.util.Date

data class TypeSyncProgress(
    @SerializedName("typeIdentifier")
    val typeIdentifier: String,
    @SerializedName("sentCount")
    var sentCount: Int,
    @SerializedName("isComplete")
    var isComplete: Boolean,
    @SerializedName("pendingAnchorData")
    var pendingAnchorData: String?
)

data class SyncStateRequest(
    @SerializedName("userKey")
    val userKey: String,
    @SerializedName("fullExport")
    val fullExport: Boolean,
    @SerializedName("createdAt")
    val createdAt: Date,
    @SerializedName("typeProgress")
    var typeProgress: Map<String, TypeSyncProgress>,
    @SerializedName("totalSentCount")
    var totalSentCount: Int = 0,
    @SerializedName("completedTypes")
    var completedTypes: Set<String>,
    @SerializedName("currentTypeIndex")
    var currentTypeIndex: Int
) {
    val hasProgress: Boolean
        get() {
            return totalSentCount > 0 || !completedTypes.isEmpty()
        }
}
