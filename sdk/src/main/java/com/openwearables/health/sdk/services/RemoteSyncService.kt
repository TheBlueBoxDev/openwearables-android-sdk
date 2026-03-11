package com.openwearables.health.sdk.services

import com.openwearables.health.sdk.managers.SecureStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.concurrent.withLock

class RemoteSyncService(
    private val secureStorage: SecureStorage
) {
    var onAuthError: ((Int, String) -> Unit)? = null

    var host: String? = null

    // MARK: - Auth Helpers
    val isApiKeyAuth: Boolean
        get() {
            return secureStorage.apiKey != null && secureStorage.accessToken == null
        }

    val authCredential: String?
        get() {
            return secureStorage.accessToken ?: secureStorage.apiKey
        }

    val hasAuth: Boolean
        get() {
            return authCredential != null
        }

    val isSyncActive: Boolean
        get() {
            return secureStorage.syncActive
        }

    // MARK: - Sync Endpoint
    private fun buildSyncEndpoint(host: String, customSyncUrl: String?, userId: String): String {
        if (customSyncUrl != null) {
            if (customSyncUrl.contains("{user_id}") || customSyncUrl.contains("{userId}")) {
                return customSyncUrl
                    .replace("{userId}", userId)
                    .replace("{user_id}", userId)
            }
            val normalizedBase = customSyncUrl.trimEnd('/')
            return "$normalizedBase/sdk/users/$userId/sync"
        }
        val h = if (host.endsWith("/")) host.dropLast(1) else host
        return "$h/api/v1/sdk/users/$userId/sync"
    }

    val apiBaseUrl: String
        get() {
            val host = secureStorage.host
            val h = if (host.endsWith("/")) host.dropLast(1) else host
            return "$h/api/v1"
        }


    // MARK: - Token Refresh

//    private suspend fun attemptTokenRefresh(): Boolean = withContext(Dispatchers.IO) {
//        tokenRefreshLock.withLock { isRefreshingToken = true }
//        try {
//            val refreshToken = secureStorage.refreshToken
//            val apiBaseUrl = secureStorage.apiBaseUrl
//            if (refreshToken == null || apiBaseUrl == null) {
//                android.util.Log.w(TAG, "No refresh token or host - cannot refresh")
//                return@withContext false
//            }
//
//            android.util.Log.d(TAG, "Attempting token refresh...")
//            val url = "$apiBaseUrl/token/refresh"
//            val body = gson.toJson(mapOf("refresh_token" to refreshToken))
//            val request = Request.Builder()
//                .url(url)
//                .post(body.toRequestBody("application/json".toMediaType()))
//                .header("Content-Type", "application/json")
//                .build()
//
//            val response = httpClient.newCall(request).execute()
//            val responseBody = response.body?.string()
//            android.util.Log.d(TAG, "Token refresh response [${response.code}]: $responseBody")
//
//            if (response.isSuccessful && responseBody != null) {
//                @Suppress("UNCHECKED_CAST")
//                val json = gson.fromJson(responseBody, Map::class.java) as? Map<String, Any>
//                val newAccessToken = json?.get("access_token") as? String
//                if (newAccessToken != null) {
//                    secureStorage.updateTokens(newAccessToken, json["refresh_token"] as? String)
//                    logger("Token refreshed")
//                    return@withContext true
//                }
//            }
//            logger("Token refresh failed (HTTP ${response.code})")
//            false
//        } catch (e: Exception) {
//            android.util.Log.e(TAG, "Token refresh error", e)
//            logger("Token refresh failed")
//            false
//        } finally {
//            tokenRefreshLock.withLock { isRefreshingToken = false }
//        }
//    }
//
//    // MARK: - Send with Auth Retry
//
//    private data class SendResult(val success: Boolean, val statusCode: Int?, val payloadSizeKb: Int)
//
//    private suspend fun sendPayload(endpoint: String, payload: Map<String, Any>): SendResult =
//        withContext(Dispatchers.IO) {
//            try {
//                val jsonBody = gson.toJson(payload)
//                val sizeKb = jsonBody.length / 1024
//                android.util.Log.d(TAG, "REQUEST [$endpoint] (${sizeKb} KB):\n${prettyGson.toJson(payload)}")
//
//                val requestBuilder = Request.Builder()
//                    .url(endpoint)
//                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
//                    .header("Content-Type", "application/json")
//                applyAuth(requestBuilder)
//
//                val response = httpClient.newCall(requestBuilder.build()).execute()
//                val responseBody = response.body?.string()
//                android.util.Log.d(TAG, "RESPONSE [${response.code}]:\n$responseBody")
//
//                if (response.isSuccessful) return@withContext SendResult(true, response.code, sizeKb)
//                if (response.code == 401) {
//                    val retryOk = handle401(endpoint, jsonBody)
//                    return@withContext SendResult(retryOk, if (retryOk) 200 else 401, sizeKb)
//                }
//
//                SendResult(false, response.code, sizeKb)
//            } catch (e: Exception) {
//                android.util.Log.e(TAG, "Upload error", e)
//                SendResult(false, null, 0)
//            }
//        }
//
//    private suspend fun handle401(endpoint: String, jsonBody: String): Boolean {
//        if (secureStorage.isApiKeyAuth) {
//            emitAuthError(401)
//            return false
//        }
//
//        if (attemptTokenRefresh()) {
//            val newCredential = secureStorage.authCredential
//            if (newCredential != null) {
//                android.util.Log.d(TAG, "Retrying upload with refreshed token...")
//                return try {
//                    val retryBuilder = Request.Builder()
//                        .url(endpoint)
//                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
//                        .header("Content-Type", "application/json")
//                    applyAuth(retryBuilder, newCredential)
//
//                    val retryResponse = httpClient.newCall(retryBuilder.build()).execute()
//                    val retryBody = retryResponse.body?.string()
//                    android.util.Log.d(TAG, "Retry RESPONSE [${retryResponse.code}]:\n$retryBody")
//
//                    if (retryResponse.isSuccessful) true
//                    else { emitAuthError(401); false }
//                } catch (e: Exception) {
//                    android.util.Log.e(TAG, "Retry failed", e)
//                    emitAuthError(401); false
//                }
//            }
//        }
//        emitAuthError(401)
//        return false
//    }

}