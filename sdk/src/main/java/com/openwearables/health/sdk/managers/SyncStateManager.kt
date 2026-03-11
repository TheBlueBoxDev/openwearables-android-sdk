package com.openwearables.health.sdk.managers

import android.util.Log
import com.openwearables.health.sdk.data.entities.SyncState
import com.openwearables.health.sdk.data.entities.TypeSyncProgress
import com.openwearables.health.sdk.services.LocalStorageService
import com.openwearables.health.sdk.services.RemoteSyncService
import kotlinx.serialization.json.Json
import java.lang.Exception

class SyncStateManager(
    val secureStorage: SecureStorage,
    val syncService: RemoteSyncService,
    val localStorageService: LocalStorageService
) {

    private val dateFormatter: java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(java.time.ZoneOffset.UTC)

    val hasResumableSyncSession: Boolean
        get () {
            val state = load() ?: return false
            return state.hasProgress
        }

    val resumeTypeIndex: Int
        get () {
            val state = load() ?: return 0
            return state.currentTypeIndex
        }

    // MARK: - Save/Load Sync State
    fun save(state: SyncState) {
        val state = Json.encodeToString(state)
        localStorageService.writeToFile(SYNC_STATE_FILE, state)
    }

    fun load(): SyncState? {
        try {
            val data = localStorageService.readFromFile(SYNC_STATE_FILE) ?: return null
            val state = Json.decodeFromString<SyncState>(data)
            if (state.userKey == secureStorage.userKey)
                return state
            else {
                Log.d(TAG,"Sync state for different user, clearing")
                clearSyncSession()
                return null
            }
        } catch (e: Exception) {
            Log.d(TAG, "SyncState file doesn't exist or is corrupted")
        }
        return null
    }

    private suspend fun processTypes(
        types: List<String>,
        startIndex: Int,
        fullExport: Boolean,
        endpoint: String
    ) {
        for (i in startIndex until types.size) {
            val type = types[i]
            if (!shouldSyncType(type)) {
                Log.d(TAG,"Skipping $type - already synced")
                continue
            }

            updateCurrentTypeIndex(i)
//            val success = processType(type, fullExport, endpoint)
//            if (!success) {
//                Log.d(TAG,"Sync paused at $type, will resume later")
//                return
//            }
        }
        finalizeSyncState()
    }

    fun updateTypeProgress(typeIdentifier: String, sentInChunk: Int, isComplete: Boolean) {
        val state = load() ?: return
        val progress = state.typeProgress[typeIdentifier] ?: TypeSyncProgress(typeIdentifier)
        progress.sentCount += sentInChunk
        progress.isComplete = isComplete

//        if (anchorTimestamp != null) progress.pendingAnchorTimestamp = anchorTimestamp
        state.typeProgress[typeIdentifier] = progress
        state.totalSentCount += sentInChunk

        if (isComplete) {
            state.completedTypes.add(typeIdentifier)
//            progress.pendingAnchorTimestamp?.let { saveAnchor(typeIdentifier, it) }
        }

        save(state)
    }

    fun updateCurrentTypeIndex(index: Int) {
        val state = load() ?: return
        state.currentTypeIndex = index
        save(state)
    }

    fun clearSyncSession() {
        localStorageService.remove(SYNC_STATE_FILE)
    }

    // MARK: - Start New Sync State
    fun startNew(fullExport: Boolean, types: Set<String>): SyncState {
        val state = SyncState(
            userKey = secureStorage.userKey,
            fullExport = fullExport,
            createdAt = System.currentTimeMillis()
        )

        save(state)
        return state
    }

    // MARK: - Finalize Sync (mark complete)
    fun finalizeSyncState() {
        load()?.let {
            if (it.fullExport) secureStorage.fullExport = true

            clearSyncSession()
        }
    }

    // MARK: - Check for Resumable Session

    fun shouldSyncType(typeIdentifier: String): Boolean {
        val state = load() ?: return true
        return !state.completedTypes.contains(typeIdentifier)
    }

    // MARK: - Get Sync Status
    val syncStatusDict: Map<String, Any?>
        get() {
            load()?.let {
                return mapOf(
                    "hasResumableSession" to it.hasProgress,
                    "sentCount" to it.totalSentCount,
                    "completedTypes" to it.completedTypes.size,
                    "isFullExport" to it.fullExport,
                    "createdAt" to dateFormatter
                        .format(java.time.Instant.ofEpochMilli(it.createdAt))
                )
            }
            return mapOf(
                "hasResumableSession" to false,
                "sentCount" to 0,
                "completedTypes" to 0,
                "isFullExport" to false,
                "createdAt" to null
            )
        }

    companion object {
        private const val TAG = "SyncStateManager"
        private const val SYNC_STATE_FILE = "state.json"
    }
}