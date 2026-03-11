package com.openwearables.health.sdk.data.entities

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.openwearables.health.sdk.NotificationConfig
import com.openwearables.health.sdk.ProviderIds
import com.openwearables.health.sdk.managers.HealthConnectManager
import com.openwearables.health.sdk.managers.SamsungHealthManager
import com.openwearables.health.sdk.managers.SecureStorage
import com.openwearables.health.sdk.managers.SyncStateManager

//class HealthSyncWorker(
//    context: Context,
//    params: WorkerParameters
//) : CoroutineWorker(context, params) {
//
//    companion object {
//        const val KEY_HOST = "host"
//        const val KEY_CUSTOM_SYNC_URL = "customSyncUrl"
//    }
//
//    override suspend fun doWork(): Result {
//        val host = inputData.getString(KEY_HOST) ?: return Result.failure()
//        val customSyncUrl = inputData.getString(KEY_CUSTOM_SYNC_URL)
//
//        try {
//            setForeground(getForegroundInfo())
//            Log.d("HealthSyncWorker", "Running as foreground service")
//        } catch (e: Exception) {
//            Log.w("HealthSyncWorker", "Could not promote to foreground: ${e.message}")
//        }
//
//        val secureStorage = SecureStorage(applicationContext)
//        val dispatchers = DefaultDispatcherProvider()
//        val provider = createProvider(applicationContext, secureStorage, dispatchers)
//        val syncManager = SyncStateManager(
//            applicationContext, secureStorage, provider, dispatchers,
//            { Log.d("HealthSyncWorker", it) }
//        )
//
//        return try {
//            val trackedTypes = secureStorage.getTrackedTypes()
//            provider.setTrackedTypes(trackedTypes)
//
//            Log.d("HealthSyncWorker", "Background sync (provider: ${provider.providerId})")
//            syncManager.syncNow(host, customSyncUrl, fullExport = false)
//
//            Result.success()
//        } catch (e: Exception) {
//            Log.e("HealthSyncWorker", "Sync failed", e)
//            Result.retry()
//        }
//    }
//
//    private fun createProvider(
//        context: Context,
//        storage: SecureStorage,
//        dispatchers: DispatcherProvider
//    ): HealthDataProvider {
//        val providerId = storage.getProvider()
//        val log: (String) -> Unit = { Log.d("HealthSyncWorker", it) }
//        return when (providerId) {
//            ProviderIds.GOOGLE -> HealthConnectManager(context, null, dispatchers, log)
//            else -> SamsungHealthManager(context, null, dispatchers, log)
//        }
//    }
//
//    override suspend fun getForegroundInfo(): ForegroundInfo {
//        createNotificationChannel()
//        val notification = NotificationCompat.Builder(applicationContext, NotificationConfig.CHANNEL_ID)
//            .setContentTitle(NotificationConfig.CHANNEL_NAME)
//            .setContentText("Syncing health data...")
//            .setSmallIcon(R.drawable.ic_popup_sync)
//            .setPriority(NotificationCompat.PRIORITY_LOW)
//            .setOngoing(true)
//            .build()
//        return ForegroundInfo(
//            NotificationConfig.NOTIFICATION_ID,
//            notification,
//            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
//        )
//    }
//
//    private fun createNotificationChannel() {
//        val channel = NotificationChannel(
//            NotificationConfig.CHANNEL_ID,
//            NotificationConfig.CHANNEL_NAME,
//            NotificationManager.IMPORTANCE_LOW
//        ).apply { description = NotificationConfig.CHANNEL_DESCRIPTION }
//        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
//            .createNotificationChannel(channel)
//    }
//}