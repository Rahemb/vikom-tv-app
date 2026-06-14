package com.example.tv_caller_app.calling.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class PermissionHelper(
    private val activity: FragmentActivity,
    private val onPermissionsResult: (allGranted: Boolean) -> Unit
) {

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    private val requiredPermissions: Array<String>
        get() {
            val permissions = mutableListOf<String>()

            permissions.add(Manifest.permission.RECORD_AUDIO)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            return permissions.toTypedArray()
        }

    fun registerPermissionLauncher(launcher: ActivityResultLauncher<Array<String>>) {
        permissionLauncher = launcher
        Log.d(TAG, "Permission launcher registered")
    }

    fun hasAllPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermissions() {
        val launcher = permissionLauncher
        if (launcher == null) {
            Log.e(TAG, "Permission launcher not registered. Call registerPermissionLauncher() first.")
            onPermissionsResult(false)
            return
        }

        val permissionsToRequest = requiredPermissions.filter { permission ->
            !hasPermission(permission)
        }.toTypedArray()

        if (permissionsToRequest.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            onPermissionsResult(true)
            return
        }

        Log.d(TAG, "Requesting permissions: ${permissionsToRequest.joinToString()}")
        launcher.launch(permissionsToRequest)
    }

    fun handlePermissionResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.all { it.value }

        if (allGranted) {
            Log.i(TAG, "All permissions granted")
        } else {
            val deniedPermissions = permissions.filter { !it.value }.keys
            Log.w(TAG, "Permissions denied: ${deniedPermissions.joinToString()}")
        }

        onPermissionsResult(allGranted)
    }

    fun getMissingPermissions(): List<String> {
        return requiredPermissions.filter { permission ->
            !hasPermission(permission)
        }
    }

    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.POST_NOTIFICATIONS -> "Notifications"
            Manifest.permission.BLUETOOTH_CONNECT -> "Bluetooth"
            else -> permission
        }
    }

    fun getPermissionErrorMessage(): String {
        val missing = getMissingPermissions()
        if (missing.isEmpty()) return ""

        val names = missing.joinToString(", ") { getPermissionDisplayName(it) }
        return "Please grant the following permissions to make calls: $names"
    }

    companion object {
        private const val TAG = "PermissionHelper"

        fun hasNotificationPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                return true
            }

            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }

        fun hasBluetoothPermission(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                return true
            }

            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
}
