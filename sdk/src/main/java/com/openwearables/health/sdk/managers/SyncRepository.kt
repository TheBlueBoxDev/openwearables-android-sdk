package com.openwearables.health.sdk.managers

import com.openwearables.health.sdk.services.LocalStorageService
import com.openwearables.health.sdk.services.SyncService


class SyncRepository(
    val syncService: SyncService, val localStorageService: LocalStorageService
) {
}