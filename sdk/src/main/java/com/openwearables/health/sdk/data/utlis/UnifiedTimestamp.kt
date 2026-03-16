package com.openwearables.health.sdk.data.utlis

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// ---------------------------------------------------------------------------
// Timestamp helpers
// ---------------------------------------------------------------------------

object UnifiedTimestamp {
    private val isoFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)

    fun fromEpochMs(epochMs: Long): String =
        isoFormatter.format(Instant.ofEpochMilli(epochMs))

    fun zoneOffsetString(): String {
        val offset = ZoneId.systemDefault().rules.getOffset(Instant.now())
        return if (offset.totalSeconds == 0) "+00:00" else offset.toString()
    }
}
