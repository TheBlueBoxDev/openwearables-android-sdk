package com.openwearables.healthsdk.managers

import android.util.Log
import com.openwearables.healthsdk.data.entities.SyncState
import com.openwearables.healthsdk.data.entities.TypeSyncProgress
import com.openwearables.healthsdk.services.LocalStorageService
import kotlinx.serialization.json.Json
import java.lang.Exception
import java.util.Date

class SyncStateManager(
    val localStorageService: LocalStorageService, val secureStorage: SecureStorageManager
) {
    // MARK: - Save/Load Sync State
    fun save(state: SyncState) {
        val state = Json.encodeToString(state)
        localStorageService.writeToFile(STATE_FILE, state)
    }

    fun load(): SyncState? {
        try {
            val data = localStorageService.readFromFile(STATE_FILE)
            val state = Json.decodeFromString<SyncState>(data)
            if (state.userKey == secureStorage.userKey)
                return state
            else {
                Log.d("OpenWearablesHealthSDK","Sync state for different user, clearing")
                clearSyncSession()
                return null
            }
        } catch (e: Exception) {
            Log.d("OpenWearablesHealthSDK", "SyncState file doesn't exist or is corrupted")
        }
        return null
    }

    fun updateTypeProgress(typeIdentifier: String, sentInChunk: Int, isComplete: Boolean) {
        val state = load() ?: return

        val progress = state.typeProgress[typeIdentifier] ?: TypeSyncProgress(
            typeIdentifier = typeIdentifier,
            sentCount = 0,
            isComplete = false
            )
        progress.sentCount += sentInChunk
        progress.isComplete = isComplete

        state.typeProgress[typeIdentifier] = progress
        state.totalSentCount += sentInChunk

        if (isComplete) state.completedTypes.add(typeIdentifier)

        save(state)
    }

    fun updateCurrentTypeIndex(index: Int) {
        val state = load() ?: return
        state.currentTypeIndex = index
        save(state)
    }

    fun clearSyncSession() {
        localStorageService.remove(STATE_FILE)
    }

    // MARK: - Start New Sync State
    fun startNew(fullExport: Boolean, types: Set<String>): SyncState {
        val state = SyncState(
            userKey = secureStorage.userKey,
            fullExport = fullExport,
            createdAt = Date(),
            typeProgress = mutableMapOf(),
            totalSentCount = 0,
            completedTypes = mutableSetOf(*types.toTypedArray()),
            currentTypeIndex = 0
        )

        save(state)
        return state
    }

    // MARK: - Finalize Sync (mark complete)
    fun finalizeSyncState() {
        load()?.let {
            if (it.fullExport) secureStorage.fullDone = true

            clearSyncSession()
        }
    }

    // MARK: - Check for Resumable Session
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
                    //            "createdAt" to ISO8601DateFormatter().string(from: state.createdAt)
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
        const val STATE_FILE = "state.json"
    }
}