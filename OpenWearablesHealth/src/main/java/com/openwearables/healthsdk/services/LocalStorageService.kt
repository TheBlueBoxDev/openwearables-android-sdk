package com.openwearables.healthsdk.services

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import kotlin.jvm.Throws

class LocalStorageService(val context: Context) {

    @Throws(FileNotFoundException::class)
    fun writeToFile(fileName: String, data: String) {
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            appendToFile(fileName, data)
        } else {
            context.openFileOutput(file.name, Context.MODE_PRIVATE).use {
                it.write(data.toByteArray())
            }
        }
    }

    @Throws(FileNotFoundException::class)
    fun readFromFile(fileName: String): String {
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            context.openFileInput(fileName)
                .bufferedReader()
                .useLines { lines ->
                    return lines.joinToString("\n")
                }
        } else {
            return ""
        }
    }

    @Throws(FileNotFoundException::class)
    fun appendToFile(fileName: String, data: String) {
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            context.openFileOutput(fileName, Context.MODE_APPEND).use {
                it.write(("\n$data").toByteArray())
            }
        } else {
            writeToFile(fileName, data)
        }
    }

    fun remove(fileName: String) {
        val file = File(context.filesDir, fileName)
        try {
            file.delete()
        } catch (e: SecurityException) {
            Log.e("OpenWearablesHealthSDK", "Error deleting $fileName -> ${e.message}")
        }
    }

    // MARK: - Clear outbox
    fun clearAll() {
        context.filesDir.list()?.forEach {
            val file = File(context.filesDir, it)
            try {
                file.delete()
            } catch (e: SecurityException) {
                Log.e("OpenWearablesHealthSDK", "Error deleting $it -> ${e.message}")
            }
        }
    }
}