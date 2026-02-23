package com.openwearables.healthsdk.managers

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import com.google.gson.Gson
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

class KeychainManager private constructor(context: Context) {
    private val preferences: SharedPreferences = context.getSharedPreferences("OpenWearablesHealthKeychain",
        Context.MODE_PRIVATE)

    var syncActive: Boolean
        get() = preferences.getBoolean(SYNC_ACTIVE, false)
        set(value) { preferences.edit()?.putBoolean(SYNC_ACTIVE, value)?.apply() }

    var trackedTypes: String
        get() = preferences.getString(TRACKED_TYPES, "") ?: ""
        set(value) { preferences.edit()?.putString(TRACKED_TYPES, value)?.apply() }

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
    fun cipherForEncryption(): Cipher {
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
    fun cipherForDecryption(initializationVector: ByteArray): Cipher {
        val cipher = getCipher().apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(128, initializationVector)
            )
        }
        return cipher
    }

//    @Throws(
//        BadPaddingException::class,
//        IllegalBlockSizeException::class
//    )
//    fun encryptData(plaintext: String, cipher: Cipher): CiphertextWrapper {
//        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
//        return CiphertextWrapper(ciphertext, cipher.iv)
//    }

    @Throws(
        BadPaddingException::class,
        IllegalBlockSizeException::class
    )
    fun decryptData(ciphertext: ByteArray, cipher: Cipher): String {
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, StandardCharsets.UTF_8)
    }

//
//    fun persistCiphertextWrapperToSharedPrefs(
//        ciphertextWrapper: CiphertextWrapper?,
//        context: Context,
//        mode: Int,
//        prefKey: String
//    ) {
//        val json = (Gson()).toJson(ciphertextWrapper)
//        context.getSharedPreferences(
//            com.core.utils.manager.CryptographyManager.SHARED_PREFS_FILENAME,
//            mode
//        ).edit().putString(prefKey, json).apply()
//    }
//
//    fun getCiphertextWrapperFromSharedPrefs(
//        context: Context,
//        mode: Int,
//        prefKey: String
//    ): CiphertextWrapper? {
//        val json = context.getSharedPreferences(
//            com.core.utils.manager.CryptographyManager.SHARED_PREFS_FILENAME,
//            mode
//        ).getString(prefKey, null)
//        return (Gson()).fromJson<CiphertextWrapper?>(json, CiphertextWrapper::class.java)
//    }

    companion object {
        const val ACCESS_TOKEN = "accessToken"
        const val REFRESH_TOKEN = "refreshToken"
        const val USER_ID = "userId"
        const val API_KEY = "apiKey"

        const val SYNC_ACTIVE = "syncActive"
        const val TRACKED_TYPES = "trackedTypes"

        const val KEY_SIZE: Int = 256
        const val KEY_NAME = "OpenWearables_Health_Android_SDK"
        const val ANDROID_KEYSTORE: String = "AndroidKeyStore"
        const val ENCRYPTION_BLOCK_MODE: String = KeyProperties.BLOCK_MODE_GCM
        const val ENCRYPTION_PADDING: String = KeyProperties.ENCRYPTION_PADDING_NONE
        const val ENCRYPTION_ALGORITHM: String = KeyProperties.KEY_ALGORITHM_AES

        var shared: KeychainManager? = null
            private set

        fun initShared(context: Context): KeychainManager {
            if (shared == null)
                shared = KeychainManager(context)
            return shared!!
        }
    }
}