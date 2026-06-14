package com.example.tv_caller_app.calling.audio

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat

class MicrophoneStatusHandler(private val activity: Activity) {

    companion object {
        private const val TAG = "MicrophoneStatusHandler"
    }

    private val context: Context = activity.applicationContext

    fun checkAndRequestPermission(
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        showRationale: Boolean = true
    ) {
        if (AudioPermissionHelper.hasAudioPermission(context)) {
            Log.d(TAG, "Audio permission already granted")
            if (AudioPermissionHelper.needsBluetoothPermission() &&
                !AudioPermissionHelper.hasBluetoothPermissions(context)) {
                Log.d(TAG, "Requesting Bluetooth permissions for mic support (Android 12+)")
                ActivityCompat.requestPermissions(
                    activity,
                    AudioPermissionHelper.getBluetoothPermissions(),
                    AudioPermissionHelper.REQUEST_CODE_BLUETOOTH_PERMISSION
                )
            }
            onGranted()
            return
        }

        if (showRationale && AudioPermissionHelper.shouldShowRationale(activity)) {
            showPermissionRationaleDialog(
                onAccept = { AudioPermissionHelper.requestAudioPermission(activity) },
                onDecline = onDenied
            )
        } else {
            AudioPermissionHelper.requestAudioPermission(activity)
        }
    }

    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        if (requestCode == AudioPermissionHelper.REQUEST_CODE_BLUETOOTH_PERMISSION) {
            if (AudioPermissionHelper.hasBluetoothPermissions(context)) {
                Log.d(TAG, "Bluetooth permissions granted — Bluetooth mic fully supported")
            } else {
                Log.w(TAG, "Bluetooth permissions denied — Bluetooth mic may not work")
                showToast("Bluetooth permission denied. Bluetooth microphones may not work.")
            }
            return
        }

        AudioPermissionHelper.handlePermissionResult(
            requestCode = requestCode,
            permissions = permissions,
            grantResults = grantResults,
            onGranted = {
                showToast("Microphone permission granted")
                onGranted()
            },
            onDenied = {
                if (AudioPermissionHelper.shouldShowRationale(activity)) {
                    showToast("Microphone permission denied. App will run in receive-only mode.")
                    onDenied()
                } else {
                    showPermanentlyDeniedDialog()
                    onDenied()
                }
            }
        )
    }

    fun handleModeChange(isMicAvailable: Boolean, message: String) {
        Log.i(TAG, "Microphone available: $isMicAvailable - $message")
        if (isMicAvailable) {
            showToast("✓ Microphone ready - 2-way calling enabled")
        } else {
            showToast("⚠ No microphone - Receive-only mode")
        }
    }

    private fun showPermissionRationaleDialog(onAccept: () -> Unit, onDecline: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle("Microphone Permission Needed")
            .setMessage(AudioPermissionHelper.getPermissionExplanation(showExtendedHelp = true))
            .setPositiveButton("Grant Permission") { _, _ -> onAccept() }
            .setNegativeButton("Not Now") { _, _ -> onDecline() }
            .setCancelable(false)
            .show()
    }

    private fun showPermanentlyDeniedDialog() {
        AlertDialog.Builder(activity)
            .setTitle("Permission Required")
            .setMessage(AudioPermissionHelper.getPermanentlyDeniedMessage())
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
