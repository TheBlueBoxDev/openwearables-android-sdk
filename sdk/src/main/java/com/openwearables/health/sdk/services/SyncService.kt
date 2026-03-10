package com.openwearables.health.sdk.services

import com.openwearables.health.sdk.managers.SecureStorageManager

class SyncService(
    private val secureStorage: SecureStorageManager
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

    val apiBaseUrl: String
        get() {
            val host = secureStorage.host
            val h = if (host.endsWith("/")) host.dropLast(1) else host
            return "$h/api/v1"
        }
}