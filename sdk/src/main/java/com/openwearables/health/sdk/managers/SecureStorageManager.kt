package com.openwearables.health.sdk.managers

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorageManager(context: Context) {

    private val securePrefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create encrypted prefs, falling back to regular prefs", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    private val configPrefs: SharedPreferences by lazy {
        context.getSharedPreferences(CONFIG_PREFS_NAME, Context.MODE_PRIVATE)
    }

    var provider: String?
        get() =  configPrefs.getString(KEY_HEALTH_PROVIDER, null)
        set(value) { configPrefs.edit { putString(KEY_HEALTH_PROVIDER, value) } }

    var host: String
        get() = configPrefs.getString(KEY_HOST, "") ?: ""
        set(value) { configPrefs.edit { putString(KEY_HOST, value) } }

    var userId: String?
        get() = securePrefs.getString(KEY_USER_ID, null)
        set(value) { securePrefs.edit { putString(KEY_USER_ID, value) } }

    var syncActive: Boolean
        get() = configPrefs.getBoolean(KEY_SYNC_ACTIVE, false)
        set(value) { configPrefs.edit { putBoolean(KEY_SYNC_ACTIVE, value) } }

    var trackedTypes: Set<String>
        get() = configPrefs.getStringSet(KEY_TRACKED_TYPES, emptySet<String>()) ?: emptySet()
        set(value) { configPrefs.edit { putStringSet(KEY_TRACKED_TYPES, value) } }

    // MARK: - Custom Sync URL
    var customSyncURL: String?
        get() = configPrefs.getString(KEY_CUSTOM_SYNC_URL, null)
        set(value) = configPrefs.edit { putString(KEY_CUSTOM_SYNC_URL, value) }

    val userKey: String
        get() {
            userId?.let { return "user.$it" }
            return "user.none"
        }

    private val fullDoneKey: String
        get() { return "fullDone.$userKey" }

    var fullExport: Boolean
        get() = configPrefs.getBoolean(fullDoneKey, false)
        set(value) = configPrefs.edit { putBoolean(fullDoneKey, value) }

    val accessToken: String?
        get() = securePrefs.getString(KEY_ACCESS_TOKEN, null)

    val refreshToken: String?
        get() = securePrefs.getString(KEY_REFRESH_TOKEN, null)

    var apiKey: String?
        get() = securePrefs.getString(KEY_API_KEY, null)
        set(value) = securePrefs.edit { putString(KEY_API_KEY, value) }

    val hasSession: Boolean
        get() {
            if (userId == null) return false
            return accessToken != null || apiKey != null
        }

    // MARK: - Public methods
    fun saveCredentials(userId: String, accessToken: String? = null, refreshToken: String? = null) {
        this.userId = userId
        securePrefs.edit {
            accessToken?.let {
                putString(KEY_ACCESS_TOKEN, accessToken)
            }
            refreshToken?.let {
                putString(KEY_REFRESH_TOKEN, refreshToken)
            }
        }
    }

    fun updateTokens(accessToken: String, refreshToken: String?) {
        securePrefs.edit { putString(KEY_ACCESS_TOKEN, accessToken) }
        refreshToken?.let {
            securePrefs.edit { putString(KEY_REFRESH_TOKEN, refreshToken) }
        }
    }

    // MARK: - Fresh Install Detection
    fun clearIfReinstalled() {
        val wasInstalled = configPrefs.getBoolean(KEY_APP_INSTALLED, false)

        if (!wasInstalled) {
            if (hasSession()) {
                Log.d(TAG, "App reinstalled - clearing stale data")
                clearAll()
            }
            configPrefs.edit { putBoolean(KEY_APP_INSTALLED, true) }
        }
    }

    fun hasSession(): Boolean {
        val userId = userId ?: return false
        return accessToken != null || apiKey != null
    }

    // MARK: - Clear
    fun clearAll() {
        securePrefs.edit { clear() }
        configPrefs.edit {
            remove(KEY_HOST)
            remove(KEY_CUSTOM_SYNC_URL)
            remove(KEY_SYNC_ACTIVE)
            remove(KEY_TRACKED_TYPES)
            remove(KEY_HEALTH_PROVIDER)
        }
    }

    companion object {
        private const val TAG = "SecureStorage"
        private const val PREFS_NAME = "com.openwearables.healthsdk.secure"
        private const val CONFIG_PREFS_NAME = "com.openwearables.healthsdk.config"

        // Secure keys (encrypted)
        private const val KEY_ACCESS_TOKEN = "accessToken"
        private const val KEY_REFRESH_TOKEN = "refreshToken"
        private const val KEY_USER_ID = "userId"
        private const val KEY_API_KEY = "apiKey"

        // Config keys (not encrypted, not sensitive)
        private const val KEY_HOST = "host"
        private const val KEY_CUSTOM_SYNC_URL = "customSyncUrl"
        private const val KEY_SYNC_ACTIVE = "syncActive"
        private const val KEY_TRACKED_TYPES = "trackedTypes"
        private const val KEY_HEALTH_PROVIDER = "healthProvider"
        private const val KEY_APP_INSTALLED = "appInstalled"
    }
}