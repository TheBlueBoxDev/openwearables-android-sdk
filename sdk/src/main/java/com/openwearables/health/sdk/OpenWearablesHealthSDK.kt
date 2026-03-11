package com.openwearables.healthsdk

import android.content.Context
import android.util.Log
import com.openwearables.health.sdk.managers.SecureStorage
import com.openwearables.health.sdk.managers.NetworkConnectionManager
import com.openwearables.health.sdk.managers.SyncStateManager
import com.openwearables.health.sdk.services.LocalStorageService
import com.openwearables.health.sdk.services.RemoteSyncService

data class HealthDataType(val id: String)

class OpenWearablesHealthSDK private constructor(val context: Context) {
    val secureStorage: SecureStorage
    val localStorageService: LocalStorageService
    val syncService: RemoteSyncService
    val syncStateManager: SyncStateManager

    init {
        NetworkConnectionManager.shared.init(context)
        secureStorage = SecureStorage(context)
        localStorageService = LocalStorageService(context)
        syncService = RemoteSyncService(secureStorage)
        syncStateManager = SyncStateManager(secureStorage, syncService, localStorageService)
    }

    val syncStatus: Map<String, Any?>
        get() = syncStateManager.syncStatusDict

    // MARK: - Configure
    fun configure(host: String) {
        secureStorage.host = host
        secureStorage.trackedTypes.let {
//            self.trackedTypes = mapTypesFromStrings(it)
        }
//
        if (syncService.isSyncActive && secureStorage.hasSession /*&& !trackedTypes.isEmpty*/)
            autoRestoreSync()
    }

    // MARK: - Auth
    fun signIn(
        userId: String, accessToken: String?, refreshToken: String?, apiKey: String?
    ) {
        val hasTokens = accessToken != null && refreshToken != null
        val hasApiKey = apiKey != null

        if (hasTokens || hasApiKey) {
            clearSyncSession()
            clearOutbox()

            secureStorage.saveCredentials(userId, accessToken, refreshToken)
            apiKey?.let {
                secureStorage.apiKey = it
            }
            return
        }
        Log.d("OpenWearablesHealthSDK", "signIn error: Provide (accessToken + refreshToken) or (apiKey)")
    }

    fun signOut() {
        stopBackgroundSync()
        clearSyncSession()
        clearOutbox()
        secureStorage.clearAll()
    }

    fun updateTokens(accessToken: String, refreshToken: String) {
        secureStorage.updateTokens(accessToken = accessToken, refreshToken = refreshToken)
//        retryOutboxIfPossible()
    }

    // MARK: - HealthKit Authorization
    suspend fun requestAuthorization(types: Array<HealthDataType>): Boolean {
//        self.trackedTypes = mapTypes(types)
//        OpenWearablesHealthSdkKeychain.saveTrackedTypes(types.map { $0.rawValue })
//        guard HKHealthStore.isHealthDataAvailable() else {
//            DispatchQueue.main.async { completion(false) }
//            return
//        }
//
//        let readTypes = Set(getQueryableTypes())
//
//        healthStore.requestAuthorization(toShare: nil, read: readTypes) { ok, _ in
//            DispatchQueue.main.async { completion(ok) }
//        }
        return true
    }

    // MARK: - Sync
    suspend fun startBackgroundSync(): Boolean {
//        guard userId != nil, hasAuth else {
//            logMessage("Cannot start sync: not signed in")
//            completion(false)
//            return
//        }
//
//        startBackgroundDelivery()
//        startNetworkMonitoring()
//        startProtectedDataMonitoring()
//
//        initialSyncKickoff { started in
//                if started {
//                    self.logMessage("Sync started")
//                } else {
//                    self.logMessage("Sync failed to start")
//                    self.isInitialSyncInProgress = false
//                }
//        }
//
//        scheduleAppRefresh()
//        scheduleProcessing()
//
//        let canStart = HKHealthStore.isHealthDataAvailable() &&
//                self.syncEndpoint != nil &&
//                self.hasAuth &&
//                !self.trackedTypes.isEmpty
//
//        if canStart {
            secureStorage.syncActive = true
//        }
        return true
    }

    fun stopBackgroundSync() {
        cancelSync()
        stopBackgroundDelivery()
        stopNetworkMonitoring()
        stopProtectedDataMonitoring()
        secureStorage.syncActive = false
    }

    suspend fun syncNow() {
//        syncAll(fullExport: false, completion: completion)
    }

    suspend fun resumeSync(): Boolean {
        return syncStateManager.hasResumableSyncSession
//        guard hasResumableSyncSession() else {
//            completion(false)
//            return
//        }
//
//        syncAll(fullExport: false) {
//            completion(true)
//        }
    }

    // MARK: - Private methods
    private fun autoRestoreSync() {
//        guard userId != nil, hasAuth else {
//            return
//        }
//
//        startBackgroundDelivery()
//        startNetworkMonitoring()
//        startProtectedDataMonitoring()
//        scheduleAppRefresh()
//        scheduleProcessing()
//
        if (syncStateManager.hasResumableSyncSession) {
//            syncAll(fullExport: false) {
//            self.logMessage("Resumed sync completed")
        }
    }

    private fun clearSyncSession() {}
    private fun clearOutbox() {}
    private fun cancelSync() {}
    private fun stopBackgroundDelivery() {}
    private fun stopNetworkMonitoring() {}
    private fun stopProtectedDataMonitoring() {}

//    internal func syncAll(fullExport: Bool, completion: @escaping () -> Void) {
//        guard !trackedTypes.isEmpty else { completion(); return }
//
//        guard self.hasAuth else {
//            self.logMessage("No auth credential for sync")
//            completion()
//            return
//        }
//        collectAllData(fullExport: fullExport, isBackground: false, completion: completion)
//    }

//    internal func collectAllData(fullExport: Bool, isBackground: Bool, completion: @escaping () -> Void) {
//        syncLock.lock()
//        if isSyncing {
//            logMessage("Sync in progress, skipping")
//            syncLock.unlock()
//            completion()
//            return
//        }
//        isSyncing = true
//        syncLock.unlock()
//
//        guard HKHealthStore.isHealthDataAvailable() else {
//            logMessage("HealthKit not available")
//            finishSync()
//            completion()
//            return
//        }
//
//        guard let credential = self.authCredential, let endpoint = self.syncEndpoint else {
//            logMessage("No auth credential or endpoint")
//            finishSync()
//            completion()
//            return
//        }
//
//        let queryableTypes = getQueryableTypes()
//        guard !queryableTypes.isEmpty else {
//            logMessage("No queryable types")
//            finishSync()
//            completion()
//            return
//        }
//
//        let typeNames = queryableTypes.map { shortTypeName($0.identifier) }.joined(separator: ", ")
//        logMessage("Types to sync (\(queryableTypes.count)): \(typeNames)")
//
//        let existingState = loadSyncState()
//        let isResuming = existingState != nil && existingState!.hasProgress
//
//                if isResuming {
//                    logMessage("Resuming sync (\(existingState!.totalSentCount) already sent, \(existingState!.completedTypes.count) types done)")
//                } else {
//                    logMessage("Starting streaming sync (fullExport: \(fullExport), \(queryableTypes.count) types)")
//                    _ = startNewSyncState(fullExport: fullExport, types: queryableTypes)
//                }
//
//        let startIndex = isResuming ? getResumeTypeIndex() : 0
//
//        processTypesSequentially(
//            types: queryableTypes,
//            typeIndex: startIndex,
//            fullExport: fullExport,
//            endpoint: endpoint,
//            credential: credential,
//            isBackground: isBackground
//        ) { [weak self] allTypesCompleted in
//            guard let self = self else { return }
//            if allTypesCompleted {
//                self.finalizeSyncState()
//            } else {
//                self.logMessage("Sync incomplete - will resume remaining types later")
//            }
//            self.finishSync()
//            completion()
//        }
//    }

}