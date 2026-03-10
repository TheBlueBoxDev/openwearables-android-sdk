package com.openwearables.health.sdk.services

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import kotlin.jvm.Throws

class LocalStorageService(val context: Context) {
    private val syncStateDir: File by lazy {
        File(context.filesDir, SYNC_STATE_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    @Throws(FileNotFoundException::class)
    fun writeToFile(fileName: String, data: String) {
        val file = File(syncStateDir, fileName)
        file.writeText(data)
    }

    @Throws(FileNotFoundException::class)
    fun readFromFile(fileName: String): String? {
        val file = File(syncStateDir, fileName)
        return if (file.exists()) {
            file.readText()
        } else {
             null
        }
    }

    fun remove(fileName: String) {
        val file = File(syncStateDir, fileName)
        try {
            file.delete()
        } catch (e: SecurityException) {
            Log.e(TAG, "Error deleting $fileName -> ${e.message}")
        }
    }

    // MARK: - Clear outbox
    fun clearAll() {
        context.filesDir.list()?.forEach {
            val file = File(syncStateDir, it)
            try {
                file.delete()
            } catch (e: SecurityException) {
                Log.e(TAG, "Error deleting $it -> ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "LocalStorageService"
        private const val SYNC_STATE_DIR = "health_sync_state"
    }
}