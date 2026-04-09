package com.openwearables.health.sdk.data.entities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import com.openwearables.health.sdk.data.NotificationConfig
import com.openwearables.health.sdk.data.ProviderIds
import com.openwearables.health.sdk.data.utlis.DefaultDispatcherProvider
import com.openwearables.health.sdk.data.utlis.DispatcherProvider
import com.openwearables.health.sdk.interfaces.HealthDataProvider
import com.openwearables.health.sdk.managers.HealthConnectManager
import com.openwearables.health.sdk.managers.SamsungHealthManager
import com.openwearables.health.sdk.managers.SecureStorage
import com.openwearables.health.sdk.managers.SyncStateManager
import com.openwearables.health.sdk.services.LocalStorageService
import com.openwearables.health.sdk.services.RemoteSyncService

/**
 * WorkManager worker for background health data synchronization.
 *
 * Scheduled as a [PeriodicWorkRequest] by [SyncStateManager]. The worker does NOT
 * manually schedule the next run — WorkManager handles periodic re-execution.
 */
class HealthSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    val secureStorage: SecureStorage by lazy { SecureStorage(context) }
    val log: (String) -> Unit = { Log.d("HealthSyncWorker", it) }

    override suspend fun doWork(): Result {
        try {
            setForeground(getForegroundInfo())
            log("Running as foreground service")
        } catch (e: Exception) {
            log("Could not promote to foreground: ${e.message}")
        }

        val dispatchers = DefaultDispatcherProvider()
        val provider = createProvider(applicationContext, secureStorage, dispatchers)
        val syncService = RemoteSyncService(secureStorage)
        val localStorageService = LocalStorageService(applicationContext)

        val syncManager = SyncStateManager(
            applicationContext, provider, secureStorage, syncService, localStorageService, log
        )

        return try {
            val trackedTypes = secureStorage.trackedTypes
            provider.setTrackedTypes(trackedTypes.toList())

            Log.d("HealthSyncWorker", "Background sync (provider: ${provider.providerId})")
            syncManager.syncNow(fullExport = false)

            Result.success()
        } catch (e: Exception) {
            Log.e("HealthSyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    private fun createProvider(
        context: Context,
        storage: SecureStorage,
        dispatchers: DispatcherProvider
    ): HealthDataProvider {
        val providerId = storage.provider
        return when (providerId) {
            ProviderIds.GOOGLE -> HealthConnectManager(context, dispatchers, log)
            else -> SamsungHealthManager(context, dispatchers, log)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, NotificationConfig.CHANNEL_ID)
            .setContentTitle(secureStorage.notificationTitle)
            .setContentText(secureStorage.notificationText)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            NotificationConfig.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NotificationConfig.CHANNEL_ID,
            NotificationConfig.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = NotificationConfig.CHANNEL_DESCRIPTION }
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }
}