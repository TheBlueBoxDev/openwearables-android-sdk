package com.openwearables.healthsdk.data.entities

import kotlinx.serialization.Serializable

@Serializable
data class OutboxItem(
    val typeIdentifier: String,
    val userKey: String,
    val payloadPath: String,
    val wasFullExport: Boolean = false
)
