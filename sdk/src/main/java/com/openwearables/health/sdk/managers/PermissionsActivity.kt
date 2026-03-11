package com.openwearables.health.sdk.managers

import android.content.Intent
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PermissionsActivity: ComponentActivity() {
    private var permissionLauncher: ActivityResultLauncher<Set<String>>? = null
    private var pendingPermissionResult: CompletableDeferred<Set<String>>? = null
    private var needed: Set<String> = emptySet()

    override fun startActivity(intent: Intent?) {
        super.startActivity(intent)

        intent?.extras?.let {
            needed = it.get("NEEDS") as? Set<String> ?: emptySet()
        }
    }

    override fun onResume() {
        super.onResume()

        CoroutineScope(Dispatchers.Main).launch {
            permissions()
            finish()
        }
    }

    override fun onStop() {
        permissionLauncher?.unregister()
        permissionLauncher = null

        super.onStop()
    }

    private suspend fun permissions() {
        try {
            val contract = PermissionController.createRequestPermissionResultContract()
            permissionLauncher = activityResultRegistry.register(
                "health_connect_permissions",
                contract
            ) { granted ->
                pendingPermissionResult?.complete(granted)
            }

            try {
                val deferred = CompletableDeferred<Set<String>>()
                pendingPermissionResult = deferred

                val launcher = permissionLauncher ?: return

                Log.d("Permissions activity", "Launching Health Connect permission dialog for ${needed.size} permissions (includes background read)")
                launcher.launch(needed)

                val granted = deferred.await()
                if (granted.size < needed.size) {
                    Log.d("Permissions activity", "Not all permissions were granted")

                }
            } catch (e: Exception) {
                Log.d("Permissions activity", "Health Connect permission request failed: ${e.message}")
            }
            pendingPermissionResult = null
        } catch (e: Exception) {
            Log.e("Permissions activity", "Failed to register HC permission launcher: ${e.message}")
        }
    }
}