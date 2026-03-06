package com.openwearables.healthsdk.managers

import com.openwearables.healthsdk.services.LocalStorageService
import com.openwearables.healthsdk.services.SyncService

class SyncRepository(
    val syncService: SyncService, val localStorageService: LocalStorageService
) {
}