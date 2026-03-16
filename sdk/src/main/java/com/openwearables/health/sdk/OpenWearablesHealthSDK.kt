package com.openwearables.health.sdk

import android.content.Context
import android.util.Log
import com.openwearables.health.sdk.interfaces.HealthDataProvider
import com.openwearables.health.sdk.managers.HealthConnectManager
import com.openwearables.health.sdk.managers.SecureStorage
import com.openwearables.health.sdk.managers.NetworkConnectionManager
import com.openwearables.health.sdk.managers.SamsungHealthManager
import com.openwearables.health.sdk.managers.SyncStateManager
import com.openwearables.health.sdk.services.LocalStorageService
import com.openwearables.health.sdk.services.RemoteSyncService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class HealthDataType(val id: String)

class OpenWearablesHealthSDK private constructor(
    val context: Context,
    samsungHealthManagerFactory: ((Context, (String) -> Unit) -> SamsungHealthManager)? = null,
    healthConnectManagerFactory: ((Context, (String) -> Unit) -> HealthConnectManager)? = null
) {
    private var syncStateManager: SyncStateManager? = null
    private var activeProvider: HealthDataProvider? = null

    // Coroutine scope — recreated if destroy() was called
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val secureStorage: SecureStorage
    val localStorageService: LocalStorageService
    val syncService: RemoteSyncService

    init {
        NetworkConnectionManager.shared.init(context)
        secureStorage = SecureStorage(context)
        localStorageService = LocalStorageService(context)
        syncService = RemoteSyncService(secureStorage)
    }

    fun isSessionValid(): Boolean = secureStorage.hasSession()

    fun isSyncActive(): Boolean = secureStorage.syncActive

    fun getStoredCredentials(): Map<String, Any?> = mapOf(
        "userId" to secureStorage.userId,
        "accessToken" to secureStorage.accessToken,
        "refreshToken" to secureStorage.refreshToken,
        "apiKey" to secureStorage.apiKey,
        "host" to secureStorage.host,
        "customSyncUrl" to secureStorage.customSyncURL,
        "isSyncActive" to secureStorage.syncActive,
        "provider" to (activeProvider?.providerId ?: secureStorage.provider)
    )

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------
    /**
     * Configure the SDK with a backend host URL.
     * @return true if background sync was previously active and should be auto-restored
     */
    fun configure(host: String, customSyncUrl: String? = null): Boolean {
        secureStorage.clearIfReinstalled()
        secureStorage.host = host
        customSyncUrl?.let {
            secureStorage.customSyncURL = it
        }

        val provider = getOrCreateProvider()
        val storedTypes = secureStorage.trackedTypes
        if (storedTypes.isNotEmpty()) {
            provider.setTrackedTypes(storedTypes.toList())
            logMessage("Restored ${storedTypes.size} tracked types for ${provider.providerName}")
        }

        logMessage("Configured: host=$host, provider=${provider.providerName}")

        val isSyncActive = secureStorage.syncActive && secureStorage.hasSession()
        if (isSyncActive && storedTypes.isNotEmpty()) {
            logMessage("Auto-restoring background sync...")
            scope.launch { autoRestoreSync() }
        }

        return isSyncActive
    }
    /**
     * Set the background sync interval in minutes. Minimum is 15 (Android limit).
     */
    fun setSyncInterval(minutes: Long) {
        ensureSyncManager().syncIntervalMinutes = minutes
        logMessage("Sync interval set to ${maxOf(minutes, SyncDefaults.MIN_SYNC_INTERVAL_MINUTES)} minutes")
    }

    private suspend fun autoRestoreSync() {
        if (secureStorage.host.isEmpty() || secureStorage.userId == null ||
            secureStorage.accessToken == null || secureStorage.apiKey == null) {
            logMessage("Cannot auto-restore: no session or host")
            return
        }
        ensureSyncManager().startBackgroundSync()
        logMessage("Background sync auto-restored")
    }
    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------
    suspend fun signIn(
        userId: String, accessToken: String?, refreshToken: String?, apiKey: String?
    ) {
        val sm = ensureSyncManager()
        sm.clearSyncSession()
        sm.resetAnchors()

        secureStorage.saveCredentials(userId, accessToken, refreshToken)
        apiKey?.let {
            secureStorage.apiKey = it
        }
        logMessage("Signed in: userId=$userId, mode=${if (accessToken != null) "token" else "apiKey"}")
    }

    suspend fun signOut() {
        logMessage("Signing out")
        val sm = ensureSyncManager()
        sm.stopBackgroundSync()
        sm.resetAnchors()
        sm.clearSyncSession()
        secureStorage.clearAll()
        activeProvider = null
        syncStateManager = null
        logMessage("Sign out complete")
    }

    fun restoreSession(): String? {
        return if (secureStorage.hasSession()) {
            val userId = secureStorage.userId
            logMessage("Session restored: userId=$userId")
            userId
        } else {
            null
        }
    }

    fun updateTokens(accessToken: String, refreshToken: String?) {
        secureStorage.updateTokens(accessToken, refreshToken)
        logMessage("Tokens updated")
//        ensureSyncManager().retryOutboxIfPossible()
    }

    suspend fun requestAuthorization(types: List<String>): Boolean {
        secureStorage.trackedTypes = types.toSet()
        val provider = getOrCreateProvider()
        provider.setTrackedTypes(types)
        logMessage("Requesting auth for ${types.size} types via ${provider.providerName}")
        return provider.requestAuthorization(types)
    }

    // -----------------------------------------------------------------------
    // Provider management
    // -----------------------------------------------------------------------
    fun setProvider(providerId: String): Boolean {
        activeProvider = when (providerId) {
            ProviderIds.SAMSUNG -> SamsungHealthManager(context, ::logMessage)
            ProviderIds.GOOGLE -> HealthConnectManager(context, ::logMessage)
            else -> {
                Log.d(TAG,"Unknown provider: $providerId")
                return false
            }
        }

        secureStorage.provider = providerId
        rebuildSyncManager()
        Log.d(TAG,"Active provider set to: ${activeProvider?.providerName}")
        return true
    }

    fun getAvailableProviders(): List<Map<String, Any>> = listOf(
        mapOf(
            "id" to ProviderIds.SAMSUNG,
            "displayName" to ProviderDisplayNames.SAMSUNG_HEALTH,
            "isAvailable" to SamsungHealthManager.isAvailable(context)
        ),
        mapOf(
            "id" to ProviderIds.GOOGLE,
            "displayName" to ProviderDisplayNames.HEALTH_CONNECT,
            "isAvailable" to HealthConnectManager.isAvailable(context)
        )
    )

    private fun rebuildSyncManager() {
        val provider = activeProvider ?: return
        syncStateManager = SyncStateManager(context, provider, secureStorage, syncService, localStorageService)
    }

    // -----------------------------------------------------------------------
    // Internal Provider
    // -----------------------------------------------------------------------

    internal fun getOrCreateProvider(): HealthDataProvider {
        activeProvider?.let { return it }
        val provider = resolveProvider(secureStorage.provider)
        activeProvider = provider
        rebuildSyncManager()
        return provider
    }

    private fun resolveProvider(providerId: String): HealthDataProvider = when (providerId) {
        ProviderIds.SAMSUNG -> SamsungHealthManager(context, ::logMessage)
        ProviderIds.GOOGLE -> HealthConnectManager(context, ::logMessage)
        else -> autoSelectProvider()
    }

    private fun autoSelectProvider(): HealthDataProvider {
        if (SamsungHealthManager.isAvailable(context)) return SamsungHealthManager(context, ::logMessage)
        return HealthConnectManager(context, ::logMessage)
    }

    @Synchronized
    internal fun ensureSyncManager(): SyncStateManager {
        if (syncStateManager == null) {
            getOrCreateProvider()
        }
        return syncStateManager!!
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    fun onForeground() {
        logMessage("App came to foreground")

        if (secureStorage.syncActive && secureStorage.hasSession()) {
            logMessage("Checking for pending sync...")
            scope.launch {
                val sm = ensureSyncManager()
                if (sm.hasResumableSyncSession) {
                    logMessage("Found interrupted sync, resuming...")
                    sm.syncNow(fullExport = false)
                }
            }
        }
    }

    fun onBackground() {
        logMessage("App went to background")

        if (secureStorage.syncActive && secureStorage.hasSession()) {
            logMessage("Scheduling background sync...")
            ensureSyncManager().scheduleExpeditedSync()
        }
    }

    fun destroy() {
        scope.cancel()
        synchronized(Companion) {
            instance = null
        }
    }

    // -----------------------------------------------------------------------
    // Sync
    // -----------------------------------------------------------------------
    @Throws(IllegalStateException::class)
    suspend fun startBackgroundSync() {
        if (secureStorage.host.isEmpty()) throw IllegalStateException("Host not configured")
        if (!secureStorage.hasSession()) throw IllegalStateException("Not signed in")

        secureStorage.syncActive = true
        logMessage("Background sync started (${getOrCreateProvider().providerName})")
        ensureSyncManager().startBackgroundSync()
    }

    suspend fun stopBackgroundSync() {
        ensureSyncManager().stopBackgroundSync()
        secureStorage.syncActive = false
        logMessage("Background sync stopped")
    }

    @Throws(IllegalStateException::class)
    suspend fun syncNow() {
        if (secureStorage.host.isEmpty()) throw IllegalStateException("Host not configured")
        ensureSyncManager().syncNow(fullExport = false)
    }

    fun resetAnchors() {
        val sm = ensureSyncManager()
        sm.resetAnchors()
        sm.clearSyncSession()
        logMessage("Anchors reset")

        if (secureStorage.syncActive &&
            (secureStorage.accessToken != null || secureStorage.apiKey != null)) {
            logMessage("Triggering full export after reset...")
            scope.launch {
                try {
                    sm.syncNow(fullExport = true)
                    logMessage("Full export after reset completed")
                } catch (e: Exception) {
                    logMessage("Full export after reset failed: ${e.message}")
                }
            }
        }
    }

    fun getSyncStatus(): Map<String, Any?> = ensureSyncManager().syncStatusDict

    @Throws(IllegalStateException::class)
    suspend fun resumeSync() {
        if (secureStorage.host.isEmpty()) throw IllegalStateException("Host not configured")
        val sm = ensureSyncManager()
        if (!sm.hasResumableSyncSession) throw IllegalStateException("No resumable sync session")
        sm.syncNow(fullExport = false)
    }

    fun clearSyncSession() {
        ensureSyncManager().clearSyncSession()
    }

    fun hasResumableSyncSession(): Boolean = ensureSyncManager().hasResumableSyncSession

    internal fun logMessage(message: String) {
        Log.d(TAG, message)
    }

    companion object {
        const val TAG = "OpenWearablesHealthSDK"

        @Volatile
        private var instance: OpenWearablesHealthSDK? = null

        fun initialize(
            context: Context,
//            dispatchers: DispatcherProvider = DefaultDispatcherProvider()
        ): OpenWearablesHealthSDK {
            return instance ?: synchronized(this) {
                instance ?: OpenWearablesHealthSDK(context.applicationContext/*, dispatchers*/).also { instance = it }
            }
        }

        /**
         * Initialize with custom factories for testability. Allows injecting mock managers.
         */
        internal fun initializeForTesting(
            context: Context,
//            dispatchers: DispatcherProvider,
            samsungFactory: ((Context, (String) -> Unit) -> SamsungHealthManager)? = null,
            healthConnectFactory: ((Context, (String) -> Unit) -> HealthConnectManager)? = null
        ): OpenWearablesHealthSDK {
            return synchronized(this) {
                OpenWearablesHealthSDK(context.applicationContext, /*dispatchers,*/ samsungFactory, healthConnectFactory).also {
                    instance = it
                }
            }
        }

        fun getInstance(): OpenWearablesHealthSDK {
            return instance ?: throw IllegalStateException(
                "SDK not initialized. Call OpenWearablesHealthSDK.initialize(context) first."
            )
        }
    }
}