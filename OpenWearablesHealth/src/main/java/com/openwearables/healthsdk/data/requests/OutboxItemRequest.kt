package com.openwearables.healthsdk.data.requests

import com.google.gson.annotations.SerializedName

data class OutboxItemRequest(
    @SerializedName("typeIdentifier")
    val typeIdentifier: String,
    @SerializedName("userKey")
    val userKey: String,
    @SerializedName("payloadPath")
    val payloadPath: String,
    @SerializedName("anchorPath")
    val anchorPath: String? = null,
    @SerializedName("wasFullExport")
    val wasFullExport: Boolean = false
)
