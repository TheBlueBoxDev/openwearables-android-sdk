package com.openwearables.health.sdk.managers

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.openwearables.health.sdk.StorageKeys
import com.openwearables.health.sdk.SyncDefaults
import com.openwearables.health.sdk.data.entities.HealthSyncWorker
import com.openwearables.health.sdk.data.entities.SyncState
import com.openwearables.health.sdk.data.entities.TypeSyncProgress
import com.openwearables.health.sdk.data.requests.HealthDataTypeRequest
import com.openwearables.health.sdk.data.utlis.UnifiedTimestamp
import com.openwearables.health.sdk.interfaces.HealthDataProvider
import com.openwearables.health.sdk.services.LocalStorageService
import com.openwearables.health.sdk.services.RemoteSyncService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.lang.Exception
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SyncStateManager(
    val context: Context,
    val healthProvider: HealthDataProvider,
    val secureStorage: SecureStorage,
    val syncService: RemoteSyncService,
    val localStorageService: LocalStorageService
) {
    private var inMemoryState: SyncState? = null
    private val stateMutex = Mutex()
    private val isSyncing = AtomicBoolean(false)

    var syncIntervalMinutes: Long = SyncDefaults.SYNC_INTERVAL_MINUTES
        set(value) {
            field = maxOf(value, SyncDefaults.MIN_SYNC_INTERVAL_MINUTES)
        }

    val hasResumableSyncSession: Boolean
        get () {
            val state = loadSyncStateFromDisk() ?: return false
            return state.hasProgress
        }

    // MARK: - Save/Load Sync State
    fun persistStateToDisk(state: SyncState) {
        val state = Json.encodeToString(state)
        localStorageService.writeToFile(SYNC_STATE_FILE, state)
    }

    fun loadSyncStateFromDisk(): SyncState? {
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

    // MARK: - Process types
    /**
     * Full-export: fetch data newest-first in chunks. Uses a while loop
     * instead of recursion to avoid continuation chain buildup on large datasets.
     */
    suspend fun processTypeNewestFirst(type: String): Boolean {
        var olderThan: Long? = null

        while (true) {
            Log.d(TAG,"  $type: querying (newest first${olderThan?.let { ", olderThan=${java.time.Instant.ofEpochMilli(it)}" } ?: ""})...")

            val result = healthProvider.readDataDescending(type, olderThan, SyncDefaults.CHUNK_SIZE)

            if (result.data.isEmpty) {
                Log.d(TAG,"  $type: all data sent (newest first)")
                stateMutex.withLock {
                    updateInMemoryProgress(type, 0, isComplete = true, anchorTimestamp = null)
                }
                return true
            }

            val count = result.data.totalCount
            val payload = HealthDataTypeRequest(
                provider = healthProvider.providerId,
                sdkVersion = SyncDefaults.SDK_VERSION,
                UnifiedTimestamp.fromEpochMs(System.currentTimeMillis()),
                data = result.data
            )
            val sendResult = syncService.sendPayload(payload)

            if (!sendResult.success) {
                val reason = sendResult.statusCode?.let { "HTTP $it" } ?: "network error"
                Log.d(TAG,"  $type: $count items -> failed ($reason)")
                return false
            }

            val anchorTs = if (olderThan == null) result.maxTimestamp else null
            val isLastChunk = count < SyncDefaults.CHUNK_SIZE

            stateMutex.withLock {
                updateInMemoryProgress(type, count, isComplete = isLastChunk, anchorTimestamp = anchorTs)
            }

            if (isLastChunk) return true

            olderThan = result.minTimestamp
        }
    }

    private suspend fun processType(type: String, fullExport: Boolean): Boolean {
        if (fullExport) { return processTypeNewestFirst(type) }

        val anchors = loadAnchors()
        val anchor = anchors[type]

        val result = healthProvider.readData(type, anchor, SyncDefaults.CHUNK_SIZE)

        if (result.data.isEmpty) {
            Log.d(TAG, "  $type: no new data")
            stateMutex.withLock {
                updateInMemoryProgress(type, 0, isComplete = true, anchorTimestamp = null)
            }
            return true
        }

        val count = result.data.totalCount
        val payload = HealthDataTypeRequest(
            provider = healthProvider.providerId,
            sdkVersion = SyncDefaults.SDK_VERSION,
            UnifiedTimestamp.fromEpochMs(System.currentTimeMillis()),
            data = result.data
        )
        val sendResult = syncService.sendPayload(payload)

        if (sendResult.success) {
            stateMutex.withLock {
                updateInMemoryProgress(type, count, isComplete = true, anchorTimestamp = result.maxTimestamp)
            }
            return true
        }
        return false
    }

    private fun finalizeSyncState() {
        val state = inMemoryState ?: return
        if (state.fullExport) secureStorage.fullExport = true
        clearSyncSession()
    }

    private suspend fun processTypes(
        types: List<String>,
        startIndex: Int,
        fullExport: Boolean
    ) {
        for (i in startIndex until types.size) {
            val type = types[i]
            val alreadySynced = stateMutex.withLock {
                inMemoryState?.completedTypes?.contains(type) == true
            }
            if (alreadySynced) {
                Log.d(TAG,"Skipping $type - already synced")
                continue
            }

            stateMutex.withLock {
                inMemoryState?.currentTypeIndex = i
            }

            val success = processType(type, fullExport)
            if (!success) {
                Log.d(TAG,"Sync paused at $type, will resume later")
                stateMutex.withLock {
                    val status = inMemoryState ?: return
                    persistStateToDisk(status)
                }
                return
            }
        }
        stateMutex.withLock {
            finalizeSyncState()
        }
    }

    private fun updateInMemoryProgress(typeIdentifier: String, sentInChunk: Int, isComplete: Boolean, anchorTimestamp: Long?) {
        val state = inMemoryState ?: return
        val progress = state.typeProgress.getOrPut(typeIdentifier) { TypeSyncProgress(typeIdentifier) }
        progress.sentCount += sentInChunk
        progress.isComplete = isComplete

        if (anchorTimestamp != null) progress.pendingAnchorTimestamp = anchorTimestamp
        state.typeProgress[typeIdentifier] = progress
        state.totalSentCount += sentInChunk

        if (isComplete) {
            state.completedTypes.add(typeIdentifier)
            progress.pendingAnchorTimestamp?.let { saveAnchor(typeIdentifier, it) }
            persistStateToDisk(state)
        }
    }

    fun clearSyncSession() {
        localStorageService.remove(SYNC_STATE_FILE)
    }

    // MARK: - Get Sync Status
    val syncStatusDict: Map<String, Any?>
        get() {
            loadSyncStateFromDisk()?.let {
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

    // MARK: - Background Sync
    suspend fun startBackgroundSync(): Boolean {
        schedulePeriodicSync()
        syncNow(fullExport = !secureStorage.fullExport)
        return true
    }

    private fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = PeriodicWorkRequestBuilder<HealthSyncWorker>(
            syncIntervalMinutes, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncDefaults.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
        Log.d(TAG,"Scheduled periodic sync every $syncIntervalMinutes minute(s)")
    }

    fun scheduleExpeditedSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val expeditedWork = OneTimeWorkRequestBuilder<HealthSyncWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncDefaults.WORK_NAME_EXPEDITED, ExistingWorkPolicy.REPLACE, expeditedWork
        )
        Log.d(TAG,"Scheduled expedited sync")
    }

    suspend fun stopBackgroundSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SyncDefaults.WORK_NAME_PERIODIC)
        Log.d(TAG,"Cancelled periodic sync")
    }

    // MARK: - Sync Now
    suspend fun syncNow(fullExport: Boolean) {
        if (!isSyncing.compareAndSet(false, true)) {
            Log.d(TAG,"Sync already in progress")
            return
        }

        try {
            val trackedTypes = healthProvider.getTrackedTypes().toList()
            if (trackedTypes.isEmpty()) {
                Log.d(TAG, "No tracked types configured")
                return
            }

            val existingState = stateMutex.withLock { loadSyncStateFromDisk() }
            val isResuming = existingState != null && existingState.hasProgress

            val startIndex: Int
            if (isResuming) {
                Log.d(TAG, "Sync: resuming (${existingState.totalSentCount} sent, ${existingState.completedTypes.size}/${trackedTypes.size} types done)")
                startIndex = existingState.currentTypeIndex
                stateMutex.withLock { inMemoryState = existingState }
            } else {
                val mode = if (fullExport) "full export" else "incremental"
                Log.d(TAG, "Sync: starting ($mode, ${trackedTypes.size} types, ${healthProvider.providerName})")
                stateMutex.withLock {
                    val state = SyncState(
                        userKey = secureStorage.userKey,
                        fullExport = fullExport,
                        createdAt = System.currentTimeMillis()
                    )
                    persistStateToDisk(state)
                    inMemoryState = state
                }
                startIndex = 0
            }

            processTypes(trackedTypes, startIndex, fullExport)
        } finally {
            isSyncing.set(false)
        }
    }

    private fun mapToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(
                value.entries.associate { (k, v) -> k.toString() to mapToJsonElement(v) }
            )
            is List<*> -> JsonArray(value.map { mapToJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }

    // MARK: - Anchors
    private val json = Json { ignoreUnknownKeys = true }

    private fun loadAnchors(): Map<String, Long> {
        val jsonStr = secureStorage.syncPrefs.getString(StorageKeys.KEY_ANCHORS, null) ?: return emptyMap()
        return try {
            val map = json.decodeFromString<Map<String, Double>>(jsonStr)
            map.mapValues { it.value.toLong() }
        } catch (_: kotlin.Exception) { emptyMap() }
    }

    private fun saveAnchor(type: String, timestamp: Long) {
        val current = loadAnchors().toMutableMap()
        current[type] = timestamp
        val element = mapToJsonElement(current)
        secureStorage.syncPrefs.edit {
            putString(
                StorageKeys.KEY_ANCHORS,
                json.encodeToString(JsonElement.serializer(), element)
            )
        }
    }

    fun resetAnchors() {
        secureStorage.fullExport = false
        secureStorage.syncPrefs.edit { remove(StorageKeys.KEY_ANCHORS) }
        clearSyncSession()
    }

    companion object {
        private const val TAG = "SyncStateManager"
        private const val SYNC_STATE_FILE = "state.json"
        private val dateFormatter: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(java.time.ZoneOffset.UTC)
    }
}