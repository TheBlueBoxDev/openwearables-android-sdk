package com.openwearables.health.sdk.managers

import android.content.Context
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.openwearables.health.sdk.data.StorageKeys
import com.openwearables.health.sdk.data.SyncDefaults
import com.openwearables.health.sdk.data.entities.HealthSyncWorker
import com.openwearables.health.sdk.data.entities.SyncState
import com.openwearables.health.sdk.data.entities.TypeSyncProgress
import com.openwearables.health.sdk.data.entities.UnifiedHealthData
import com.openwearables.health.sdk.data.requests.HealthDataTypeRequest
import com.openwearables.health.sdk.data.utlis.UnifiedTimestamp
import com.openwearables.health.sdk.interfaces.HealthDataProvider
import com.openwearables.health.sdk.services.LocalStorageService
import com.openwearables.health.sdk.services.RemoteSyncService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.lang.Exception
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SyncStateManager(
    val context: Context,
    val healthProvider: HealthDataProvider,
    val secureStorage: SecureStorage,
    val syncService: RemoteSyncService,
    val localStorageService: LocalStorageService,
    private val logger: (String) -> Unit
) {
    // MARK: - Round-Robin Sync Orchestration (combined payloads)
    private data class FetchResult(
        val type: String,
        val data: UnifiedHealthData = UnifiedHealthData(),
        val count: Int = 0,
        val nextCursor: Long? = null,
        val anchorTimestamp: Long? = null,
        val isDone: Boolean = false
    )

    private var inMemoryState: SyncState? = null
    private val stateMutex = Mutex()
    private val isSyncing = AtomicBoolean(false)

    var syncIntervalMinutes: Long = SyncDefaults.SYNC_INTERVAL_MINUTES
        set(value) {
            field = maxOf(value, SyncDefaults.MIN_SYNC_INTERVAL_MINUTES)
        }

    val hasResumableSyncSession: Boolean
        get() {
            val state = loadSyncStateFromDisk() ?: return false
            return state.hasProgress
        }

    // MARK: - Sync Start Timestamp
    /**
     * Computes the earliest epoch-ms timestamp to sync from, based on persisted `syncDaysBack`.
     * Returns the start of the day (midnight local time) that many days ago,
     * or `null` if full sync (no limit) is configured.
     */
    private fun syncStartTimestamp(): Long? {
        val daysBack = secureStorage.syncDaysBack
        if (daysBack <= 0) return null
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        cal.add(java.util.Calendar.DAY_OF_YEAR, -daysBack)
        return cal.timeInMillis
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
                logger("Sync state for different user, clearing")
                clearSyncSession()
                return null
            }
        } catch (_: Exception) {
            logger("SyncState file doesn't exist or is corrupted")
        }
        return null
    }

    // MARK: - Process types

    private suspend fun processTypesRoundRobin(
        types: List<String>,
        fullExport: Boolean
    )  {
        val olderThanCursors = mutableMapOf<String, Long?>()
        val anchorCursors = mutableMapOf<String, Long?>()
        val completedTypes = mutableSetOf<String>()

        stateMutex.withLock {
            inMemoryState?.let { state ->
                completedTypes.addAll(state.completedTypes)
                for ((id, progress) in state.typeProgress) {
                    if (!progress.isComplete) {
                        progress.pendingOlderThan?.let { olderThanCursors[id] = it }
                        progress.pendingAnchorTimestamp?.let { anchorCursors[id] = it }
                    }
                }
            }
        }

        if (!fullExport) {
            val anchors = loadAnchors()
            val floor = syncStartTimestamp()
            for (type in types) {
                if (!completedTypes.contains(type) && !anchorCursors.containsKey(type)) {
                    val storedAnchor = anchors[type]
                    val anchor = when {
                        storedAnchor != null && floor != null -> maxOf(storedAnchor, floor)
                        storedAnchor != null -> storedAnchor
                        else -> floor
                    }
                    anchorCursors[type] = anchor
                }
            }
        }

        while (true) {
            val incompleteTypes = types.filter { !completedTypes.contains(it) }
            if (incompleteTypes.isEmpty()) break

            val perTypeLimit = maxOf(1, SyncDefaults.CHUNK_SIZE / incompleteTypes.size)

            // Phase 1: Fetch one chunk from each type (no network yet)
            val roundResults = mutableListOf<FetchResult>()

            for (type in incompleteTypes) {
                val result = if (fullExport) {
                    fetchOneChunkNewestFirst(type, olderThanCursors[type], perTypeLimit)
                } else {
                    fetchOneChunkIncremental(type, anchorCursors[type], perTypeLimit)
                }

                roundResults.add(result)

                if (result.isDone) {
                    completedTypes.add(type)
                } else {
                    if (fullExport) olderThanCursors[type] = result.nextCursor
                    else anchorCursors[type] = result.nextCursor
                }
            }

            // Phase 2: Merge all fetched data into one combined payload
            val mergedData = UnifiedHealthData(
                records = roundResults.flatMap { it.data.records },
                workouts = roundResults.flatMap { it.data.workouts },
                sleep = roundResults.flatMap { it.data.sleep }
            )

            if (!mergedData.isEmpty) {
                val payload = HealthDataTypeRequest(secureStorage.provider, data = mergedData)
                val sendResult = syncService.sendPayload(payload)

                if (!sendResult.success) {
                    val reason = sendResult.statusCode?.let { "HTTP $it" } ?: "network error"
                    logger("Combined round failed ($reason)")
                    stateMutex.withLock {
                        inMemoryState?.let { persistStateToDisk(it) }
                    }
                    return
                }

                logger("Round sent: ${mergedData.totalCount} items) -> ${sendResult.statusCode}")
            }

            // Phase 3: Update progress for all types in this round
            stateMutex.withLock {
                for (result in roundResults) {
                    updateInMemoryProgress(result.type, result.count, isComplete = result.isDone, anchorTimestamp = result.anchorTimestamp)
                    if (fullExport && !result.isDone) {
                        inMemoryState?.typeProgress?.get(result.type)?.pendingOlderThan = result.nextCursor
                    }
                }
                inMemoryState?.let { persistStateToDisk(it) }
            }
        }

        stateMutex.withLock {
            val state = inMemoryState ?: return

            if (state.fullExport) secureStorage.fullExport = true
            logger("Sync: complete (${state.totalSentCount} items, ${state.completedTypes.size} types)")
            clearSyncSession()
        }
    }

    // MARK: - Fetch-Only Chunk Processors (no network)
    private suspend fun fetchOneChunkNewestFirst(
        type: String,
        olderThan: Long?,
        limit: Int
    ): FetchResult {
        val floor = syncStartTimestamp()
        val floorIso = floor?.let { UnifiedTimestamp.fromEpochMs(it) }

        logger("  $type: querying (newest first, limit=$limit${olderThan?.let { ", olderThan=${java.time.Instant.ofEpochMilli(it)}" } ?: ""})...")

        val result = healthProvider.readDataDescending(type, olderThan, limit)

        if (result.data.isEmpty) {
            logger("  $type: all data sent (newest first)")
            return FetchResult(type = type, isDone = true)
        }

        val reachedFloor = floor != null && result.minTimestamp != null && result.minTimestamp <= floor
        val isLastChunk = result.data.totalCount < limit || reachedFloor

        val data = if (reachedFloor && floorIso != null) result.data.filterSince(floorIso) else result.data

        if (data.isEmpty) {
            logger("  $type: all data within range sent")
            return FetchResult(type = type, isDone = true)
        }

        val anchorTs = if (olderThan == null) result.maxTimestamp else null
        val nextOlderThan = if (isLastChunk) null else result.minTimestamp

        logger("  $type: ${data.totalCount} samples (newest first)")

        return FetchResult(
            type = type, data = data, count = data.totalCount,
            nextCursor = nextOlderThan, anchorTimestamp = anchorTs, isDone = isLastChunk
        )
    }

    private suspend fun fetchOneChunkIncremental(
        type: String,
        anchor: Long?,
        limit: Int
    ): FetchResult {
        logger("  $type: querying (limit=$limit)...")

        val result = healthProvider.readData(type, anchor, limit)

        if (result.data.isEmpty) {
            logger("  $type: no new data")
            return FetchResult(type = type, isDone = true)
        }

        val count = result.data.totalCount
        val isLastChunk = count < limit

        logger("  $type: $count samples")

        return FetchResult(
            type = type, data = result.data, count = count,
            nextCursor = result.maxTimestamp, anchorTimestamp = result.maxTimestamp,
            isDone = isLastChunk
        )
    }

    private fun updateInMemoryProgress(typeIdentifier: String, sentInChunk: Int, isComplete: Boolean, anchorTimestamp: Long?) {
        val state = inMemoryState ?: return
        val progress = state.typeProgress.getOrPut(typeIdentifier) { TypeSyncProgress(typeIdentifier) }
        progress.sentCount += sentInChunk
        progress.isComplete = isComplete

        if (anchorTimestamp != null) progress.pendingAnchorTimestamp = anchorTimestamp
        state.totalSentCount += sentInChunk

        if (isComplete) {
            state.completedTypes.add(typeIdentifier)
            progress.pendingAnchorTimestamp?.let { saveAnchor(typeIdentifier, it) }
        }
    }

    fun clearSyncSession() {
        inMemoryState = null
        localStorageService.remove(SYNC_STATE_FILE)
        logger("Cleared sync state")
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
        scheduleExpeditedSync()
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
        logger("Scheduled periodic sync every $syncIntervalMinutes minute(s)")
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
        logger("Scheduled expedited sync")
    }

    suspend fun stopBackgroundSync() {
        WorkManager.getInstance(context).cancelUniqueWork(SyncDefaults.WORK_NAME_PERIODIC)
        logger("Cancelled periodic sync")
    }

    // MARK: - Sync Now
    suspend fun syncNow(fullExport: Boolean) {
        if (!isSyncing.compareAndSet(false, true)) {
            logger("Sync already in progress")
            return
        }

        try {
            val trackedTypes = healthProvider.getTrackedTypes().toList()
            if (trackedTypes.isEmpty()) {
                logger("No tracked types configured")
                return
            }

            val existingState = stateMutex.withLock { loadSyncStateFromDisk() }
            val isResuming = existingState != null && existingState.hasProgress

            val floor = syncStartTimestamp()
            val floorLabel = if (floor != null) "since ${java.time.Instant.ofEpochMilli(floor)}" else "full history"

            if (isResuming) {
                logger("Sync: resuming (${existingState.totalSentCount} sent, ${existingState.completedTypes.size}/${trackedTypes.size} types done, $floorLabel))")
                stateMutex.withLock { inMemoryState = existingState }
            } else {
                val mode = if (fullExport) "full export" else "incremental"
                logger("Sync: starting ($mode, ${trackedTypes.size} types, ${healthProvider.providerName}, $floorLabel)")
                stateMutex.withLock {
                    val state = SyncState(
                        userKey = secureStorage.userKey,
                        fullExport = fullExport,
                        createdAt = System.currentTimeMillis()
                    )
                    persistStateToDisk(state)
                    inMemoryState = state
                }
            }

            processTypesRoundRobin(trackedTypes, fullExport)
        } finally {
            isSyncing.set(false)
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
        secureStorage.syncPrefs.edit {
            putString(
                StorageKeys.KEY_ANCHORS,
                json.encodeToString(current.mapValues { it.value.toDouble() })
            )
        }
    }

    fun resetAnchors() {
        secureStorage.fullExport = false
        secureStorage.syncPrefs.edit { remove(StorageKeys.KEY_ANCHORS) }
        clearSyncSession()
        logger("Anchors reset - will perform full sync on next sync")
    }

    fun retryOutboxIfPossible() { /* reserved for future use */ }

    companion object {
        private const val SYNC_STATE_FILE = "state.json"
        private val dateFormatter: java.time.format.DateTimeFormatter =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                .withZone(java.time.ZoneOffset.UTC)
    }
}