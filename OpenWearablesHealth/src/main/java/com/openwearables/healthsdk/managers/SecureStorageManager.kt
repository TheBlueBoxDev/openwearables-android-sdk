package com.openwearables.healthsdk.managers

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.serialization.Serializable
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.InvalidAlgorithmParameterException
import java.security.InvalidKeyException
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.NoSuchProviderException
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.NoSuchPaddingException
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureStorageManager(context: Context) {
    @Serializable
    private data class KeychainCipherTextWrapper(
        val encryptedText: ByteArray,
        val encryptKey: ByteArray
    ) {
        override fun hashCode(): Int {
            val var1 = (this.encryptedText.contentHashCode()) * 31
            return var1 + (this.encryptKey.contentHashCode())
        }

        override fun equals(other: Any?): Boolean {
            return this === other
        }
    }

    private val preferences: SharedPreferences = context.getSharedPreferences("OpenWearablesHealthKeychain",
        Context.MODE_PRIVATE)

    var host: String
        get() = preferences.getString(HOST, "") ?: ""
        set(value) { preferences.edit { putString(HOST, value) } }

    var userId: String?
        get() = preferences.getString(USER_ID, null)
        set(value) { preferences.edit { putString(TRACKED_TYPES, value) } }

    var syncActive: Boolean
        get() = preferences.getBoolean(SYNC_ACTIVE, false)
        set(value) { preferences.edit { putBoolean(SYNC_ACTIVE, value) } }

    var trackedTypes: Set<String>
        get() = preferences.getStringSet(TRACKED_TYPES, emptySet<String>()) ?: emptySet()
        set(value) { preferences.edit { putStringSet(TRACKED_TYPES, value) } }

    var fullDone: Boolean
        get() = preferences.getBoolean(fullDoneKey, false)
        set(value) { preferences.edit { putBoolean(fullDoneKey, value) } }

    val userKey: String
        get() {
            userId?.let {
                return "user.$it"
            }
            return "user.none"
        }

    private val fullDoneKey: String
        get() {
            return "fullDone.$userKey"
        }

    val accessToken: String?
        get() = load(ACCESS_TOKEN)

    val refreshToken: String?
        get() = load(REFRESH_TOKEN)

    val apiKey: String?
        get() = load(API_KEY)

    val hasSession: Boolean
        get() {
            if (userId == null) return false
            return accessToken != null || apiKey != null
        }

    
    @Throws(
        KeyStoreException::class,
        CertificateException::class,
        NoSuchAlgorithmException::class,
        IOException::class,
        UnrecoverableKeyException::class,
        NoSuchProviderException::class,
        InvalidAlgorithmParameterException::class
    )
    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        var key: Key?
        try {
            key = keyStore.getKey(KEY_NAME, null)
            if (key != null) return key as SecretKey
        } catch (_: UnrecoverableKeyException) {
            keyStore.deleteEntry(KEY_NAME)
        }

        // if you reach here, then a new SecretKey must be generated for that keyName
        val keyGenParams = KeyGenParameterSpec.Builder(
            KEY_NAME, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(ENCRYPTION_BLOCK_MODE)
            setEncryptionPaddings(ENCRYPTION_PADDING)
            setKeySize(KEY_SIZE)
            setUserAuthenticationRequired(true)
        }
        .build()

        return KeyGenerator.getInstance("AES", ANDROID_KEYSTORE).apply {
            init(keyGenParams)
        }.generateKey()
    }

    @Throws(
        NoSuchPaddingException::class,
        NoSuchAlgorithmException::class
    )
    private fun getCipher(): Cipher {
        return Cipher.getInstance(
            "$ENCRYPTION_ALGORITHM/$ENCRYPTION_BLOCK_MODE/$ENCRYPTION_PADDING"
        )
    }

    @Throws(
        InvalidKeyException::class,
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        CertificateException::class,
        UnrecoverableKeyException::class,
        KeyStoreException::class,
        NoSuchProviderException::class,
        InvalidAlgorithmParameterException::class,
        IOException::class
    )
    private fun cipherForEncryption(): Cipher {
        val cipher = getCipher()
        try {
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        } catch (_: KeyPermanentlyInvalidatedException) {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                deleteEntry(KEY_NAME)
            }
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        }
        return cipher
    }

    @Throws(
        InvalidAlgorithmParameterException::class,
        InvalidKeyException::class,
        NoSuchAlgorithmException::class,
        NoSuchPaddingException::class,
        UnrecoverableKeyException::class,
        CertificateException::class,
        NoSuchProviderException::class,
        KeyStoreException::class,
        IOException::class
    )
    private fun cipherForDecryption(initializationVector: ByteArray): Cipher {
        val cipher = getCipher().apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(128, initializationVector)
            )
        }
        return cipher
    }

    @Throws(
        BadPaddingException::class,
        IllegalBlockSizeException::class
    )
    private fun encryptData(plaintext: String): KeychainCipherTextWrapper {
        val cipher = cipherForEncryption()
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        return KeychainCipherTextWrapper(ciphertext, cipher.iv)
    }

    fun save(key: String, value: String) {
        val ciphertextWrapper = encryptData(value)
        val json = (Gson()).toJson(ciphertextWrapper)
        preferences.edit { putString(key, json) }
    }

    @Throws(
        BadPaddingException::class,
        IllegalBlockSizeException::class
    )
    private fun decryptData(data: KeychainCipherTextWrapper): String {
        val cipher = cipherForDecryption(data.encryptKey)
        val plaintext = cipher.doFinal(data.encryptedText)
        return String(plaintext, StandardCharsets.UTF_8)
    }

    fun load(key: String): String? {
        preferences.getString(key, null)?.let {
            try {
                val data = Gson().fromJson<KeychainCipherTextWrapper>(
                    it, KeychainCipherTextWrapper::class.java
                )
                return decryptData(data)
            } catch (_: JsonSyntaxException) {
                return null
            }
        }
        return null
    }

    // MARK: - Public methods
    fun saveCredentials(userId: String, accessToken: String? = null, refreshToken: String? = null) {
        this.userId = userId
        accessToken?.let {
            save(key = ACCESS_TOKEN, value = accessToken)
        }
        refreshToken?.let {
            save(key = REFRESH_TOKEN, value = refreshToken)
        }
    }

    fun updateTokens(accessToken: String, refreshToken: String?) {
        save(key = ACCESS_TOKEN, value = accessToken)
        refreshToken?.let {
            save(key = REFRESH_TOKEN, value = refreshToken)
        }
    }

    fun saveApiKey(apiKey: String) = save(key = API_KEY, value = apiKey)

    fun clearAll() = preferences.edit { clear() }

    companion object Companion {
        const val ACCESS_TOKEN = "accessToken"
        const val REFRESH_TOKEN = "refreshToken"
        const val USER_ID = "userId"
        const val API_KEY = "apiKey"

        const val HOST = "host"
        const val SYNC_ACTIVE = "syncActive"
        const val TRACKED_TYPES = "trackedTypes"

        const val KEY_SIZE: Int = 256
        const val KEY_NAME = "OpenWearables_Health_Android_SDK"
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
        const val ENCRYPTION_BLOCK_MODE: String = KeyProperties.BLOCK_MODE_GCM
        const val ENCRYPTION_PADDING: String = KeyProperties.ENCRYPTION_PADDING_NONE
        const val ENCRYPTION_ALGORITHM: String = KeyProperties.KEY_ALGORITHM_AES
    }
}