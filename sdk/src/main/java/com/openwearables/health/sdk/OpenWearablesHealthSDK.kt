package com.openwearables.health.sdk

import android.content.Context
import android.util.Log
import com.openwearables.health.sdk.data.ProviderDisplayNames
import com.openwearables.health.sdk.data.ProviderIds
import com.openwearables.health.sdk.data.SyncDefaults
import com.openwearables.health.sdk.data.utlis.DefaultDispatcherProvider
import com.openwearables.health.sdk.data.utlis.DispatcherProvider
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
import kotlin.invoke

/**
 * Controls which log messages the SDK emits.
 *
 * - [NONE]:   No logs at all.
 * - [ALWAYS]: Logs are always emitted (Logcat + listener).
 * - [DEBUG]:  Logs are emitted only in debug builds (the default).
 */
enum class OWLogLevel { NONE, ALWAYS, DEBUG }

/**
 * Main entry point for the Open Wearables Health SDK.
 *
 * Provides a unified API for reading health data from Samsung Health and
 * Health Connect, and syncing it to a backend.
 *
 * Usage:
 * ```
 * val sdk = OpenWearablesHealthSDK.initialize(context)
 * sdk.configure("https://api.example.com")
 * sdk.signIn(userId, accessToken, refreshToken, null)
 * sdk.requestAuthorization(listOf("steps", "heartRate"))
 * sdk.startBackgroundSync()
 * ```
 */
class OpenWearablesHealthSDK private constructor(
    val context: Context,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider(),
    samsungHealthManagerFactory: ((Context, DispatcherProvider, (String) -> Unit) -> SamsungHealthManager)? = null,
    healthConnectManagerFactory: ((Context, DispatcherProvider, (String) -> Unit) -> HealthConnectManager)? = null
) {
    // Listeners
    var logListener: ((String) -> Unit)? = null
    var authErrorListener: ((statusCode: Int, message: String) -> Unit)? = null

    /// Current log level. Default is DEBUG (logs only in debuggable builds).
    var logLevel: OWLogLevel = OWLogLevel.DEBUG

    private var syncStateManager: SyncStateManager? = null
    private var activeProvider: HealthDataProvider? = null

    // Coroutine scope — recreated if destroy() was called
    private var scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    internal val secureStorage: SecureStorage by lazy { SecureStorage(context) }
    internal val localStorageService: LocalStorageService by lazy { LocalStorageService(context) }
    internal val syncService: RemoteSyncService by lazy { RemoteSyncService(secureStorage) }

    init {
        NetworkConnectionManager.shared.init(context)
    }

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

    /**
     * Customize the foreground notification shown during background sync.
     *
     * @param title Notification title (default: "Health Sync")
     * @param text  Notification body text (default: "Syncing health data...")
     */
    fun setSyncNotification(title: String? = null, text: String? = null) {
        title?.let { secureStorage.notificationTitle = it }
        text?.let { secureStorage.notificationText = it }
        logMessage("Sync notification updated: title=${title ?: "(unchanged)"}, text=${text ?: "(unchanged)"}")
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
            logMessage("API key saved")
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

    fun isSessionValid(): Boolean = secureStorage.hasSession()

    fun isSyncActive(): Boolean = secureStorage.syncActive

    fun updateTokens(accessToken: String, refreshToken: String?) {
        secureStorage.updateTokens(accessToken, refreshToken)
        logMessage("Tokens updated")
        ensureSyncManager().retryOutboxIfPossible()
    }

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

    suspend fun requestAuthorization(types: List<String>): Boolean {
        secureStorage.trackedTypes = types.toSet()
        val provider = getOrCreateProvider()
        provider.setTrackedTypes(types)
        logMessage("Requesting auth for ${types.size} types via ${provider.providerName}")
        return provider.requestAuthorization(types)
    }

    // -----------------------------------------------------------------------
    // Sync
    // -----------------------------------------------------------------------

    /**
     * Start background health data synchronization.
     *
     * @param syncDaysBack How many days back to sync. Syncs from the start of the day
     *   that many days ago (inclusive). When `null` (the default), syncs all available history.
     */
    @Throws(IllegalStateException::class)
    suspend fun startBackgroundSync(syncDaysBack: Int? = null) {
        if (secureStorage.host.isEmpty()) throw IllegalStateException("Host not configured")
        if (!secureStorage.hasSession()) throw IllegalStateException("Not signed in")

        syncDaysBack?.let {
            secureStorage.syncDaysBack = syncDaysBack
            logMessage("Sync days back set to $syncDaysBack")
        }

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

    // -----------------------------------------------------------------------
    // Provider management
    // -----------------------------------------------------------------------

    fun setProvider(providerId: String): Boolean {
        activeProvider = when (providerId) {
            ProviderIds.SAMSUNG -> {
                if (SamsungHealthManager.isAvailable(context))
                    SamsungHealthManager(context, dispatchers, ::logMessage)
                else {
                    logMessage("Provider $providerId is not available on this device")
                    return false
                }
            }
            ProviderIds.GOOGLE -> {
                if (HealthConnectManager.isAvailable(context))
                    HealthConnectManager(context, dispatchers, ::logMessage)
                else {
                    logMessage("Provider $providerId is not available on this device")
                    return false
                }
            }
            else -> {
                Log.d(TAG,"Unknown provider: $providerId")
                return false
            }
        }

        secureStorage.provider = providerId
        rebuildSyncManager()
        logMessage("Active provider set to: ${activeProvider?.providerName}")
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
        ProviderIds.SAMSUNG -> SamsungHealthManager(context, dispatchers, ::logMessage)
        ProviderIds.GOOGLE -> HealthConnectManager(context, dispatchers, ::logMessage)
        else -> autoSelectProvider()
    }

    private fun autoSelectProvider(): HealthDataProvider {
        if (SamsungHealthManager.isAvailable(context)) return SamsungHealthManager(context, dispatchers,::logMessage)
        return HealthConnectManager(context, dispatchers, ::logMessage)
    }

    private fun rebuildSyncManager() {
        val provider = activeProvider ?: return
        syncStateManager = SyncStateManager(context, provider, secureStorage, syncService, localStorageService, ::logMessage)
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

    internal fun logMessage(message: String) {
        when (logLevel) {
            OWLogLevel.NONE -> return
            OWLogLevel.ALWAYS -> { /* proceed */ }
            OWLogLevel.DEBUG -> {
                val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
                if (!isDebuggable) return
            }
        }
        Log.d(TAG, message)
        logListener?.invoke(message)
    }

    companion object {
        const val TAG = "OpenWearablesHealthSDK"

        @Volatile
        private var instance: OpenWearablesHealthSDK? = null

        fun initialize(
            context: Context,
            dispatchers: DispatcherProvider = DefaultDispatcherProvider()
        ): OpenWearablesHealthSDK {
            return instance ?: synchronized(this) {
                instance ?: OpenWearablesHealthSDK(context.applicationContext, dispatchers).also { instance = it }
            }
        }

        /**
         * Initialize with custom factories for testability. Allows injecting mock managers.
         */
        internal fun initializeForTesting(
            context: Context,
            dispatchers: DispatcherProvider,
            samsungFactory: ((Context, DispatcherProvider, (String) -> Unit) -> SamsungHealthManager)? = null,
            healthConnectFactory: ((Context, DispatcherProvider, (String) -> Unit) -> HealthConnectManager)? = null
        ): OpenWearablesHealthSDK {
            return synchronized(this) {
                OpenWearablesHealthSDK(
                    context.applicationContext,
                    dispatchers,
                    samsungFactory,
                    healthConnectFactory
                ).also {
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