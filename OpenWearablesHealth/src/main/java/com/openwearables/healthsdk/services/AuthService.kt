package com.openwearables.healthsdk.services

class AuthService {
    var onAuthError: ((Int, String) -> Unit)? = null

    var host: String? = null

    // MARK: - User State (loaded from Keychain)
    var userId: String? = null
    var accessToken: String? = null
    var refreshToken: String? = null
    var apiKey: String? = null

    // MARK: - Auth Helpers

    val isApiKeyAuth: Boolean
        get() {
            return apiKey != null && accessToken == null
        }

    val authCredential: String?
        get() {
            return accessToken ?: apiKey
        }

    val hasAuth: Boolean
        get() {
            return authCredential != null
        }

}