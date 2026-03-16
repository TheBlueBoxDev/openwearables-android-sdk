package com.openwearables.health.sdk.services

import android.util.Log
import com.openwearables.health.sdk.data.requests.HealthDataTypeRequest
import com.openwearables.health.sdk.data.requests.RefreshTokenRequest
import com.openwearables.health.sdk.managers.SecureStorage
import com.openwearables.health.sdk.services.apis.AuthApi
import com.openwearables.health.sdk.services.apis.SyncApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.getValue

class RemoteSyncService(
    private val secureStorage: SecureStorage,
    private val onAuthError: ((Int, String) -> Unit)? = null
) {
    // MARK: - Sync Endpoint
    private val syncEndpoint: String? by lazy {
        val userId = secureStorage.userId ?: return@lazy null
        secureStorage.customSyncURL?.let {
            if (it.contains("{user_id}") || it.contains("{userId}")) {
                return@lazy it
                    .replace("{userId}", userId)
                    .replace("{user_id}", userId)
            }
            val normalizedBase = it.trimEnd('/')
            return@lazy "$normalizedBase/sdk/users/$userId/sync"
        } ?: run {
            val h = if (secureStorage.host.endsWith("/")) secureStorage.host.dropLast(1) else secureStorage.host
            return@lazy "$h/api/v1/sdk/users/$userId/sync"
        }
    }

    private val authAPI: AuthApi? by lazy {
        val endpoint = syncEndpoint ?: return@lazy null
        NetworkBaseService(apiBaseUrl = endpoint).create(AuthApi::class.java)
    }

    private val syncAPI: SyncApi? by lazy {
        val endpoint = syncEndpoint ?: return@lazy null
        NetworkBaseService(apiBaseUrl = endpoint).create(SyncApi::class.java)
    }

    // MARK: - Auth Helpers
    val isApiKeyAuth: Boolean
        get() {
            return secureStorage.apiKey != null && secureStorage.accessToken == null
        }

    val isSyncActive: Boolean
        get() {
            return secureStorage.syncActive
        }

    // MARK: - Token Refresh
    private suspend fun attemptTokenRefresh(): Boolean = withContext(Dispatchers.IO) {
        val oldRefreshToken = secureStorage.refreshToken ?: return@withContext false
        try {
            return@withContext authAPI?.refreshTokens(RefreshTokenRequest(oldRefreshToken))
                ?.body()?.let {
                    secureStorage.updateTokens(it.accessToken, it.refreshToken)
                    return@let true
                } ?: false
        } catch (_: Exception) {
            return@withContext false
        }
    }

    // MARK: - Send with Auth Retry
    data class SendResult(val success: Boolean, val statusCode: Int?)

    suspend fun sendPayload(payload: HealthDataTypeRequest): SendResult =
        withContext(Dispatchers.IO) {
            val userId = secureStorage.userId ?: return@withContext SendResult(false, 401)
            try {
                val response = syncAPI?.syncHealthData(
                    secureStorage.apiKey,
                    secureStorage.accessToken,
                    userId, payload
                )

                response?.let {
                    if (response.isSuccessful) return@withContext SendResult(true, response.code())
                    if (response.code() == 401) {
                        val retryOk = handle401(payload)
                        return@withContext SendResult(retryOk, if (retryOk) 200 else 401)
                    }
                }
                SendResult(false, 301)
            } catch (e: Exception) {
                Log.e(TAG, "Upload error", e)
                SendResult(false, null)
            }
        }

    private suspend fun handle401(payloadToRetry: HealthDataTypeRequest): Boolean {
        if (isApiKeyAuth) {
            onAuthError?.invoke(401, "Unauthorized - please re-authenticate")
            return false
        }

        if (attemptTokenRefresh()) {
            Log.d(TAG, "Retrying upload with refreshed token...")
            val retryResponse = sendPayload(payloadToRetry)

            if (retryResponse.success)
                return true
            else {
                onAuthError?.invoke(401, "Retry failed")
                return false
            }
        }
        onAuthError?.invoke(401, "Unauthorized - please re-authenticate")
        return false
    }

    companion object {
        private const val TAG = "Remote Sync Service"
    }
}