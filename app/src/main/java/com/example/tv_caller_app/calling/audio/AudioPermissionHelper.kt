package com.example.tv_caller_app.calling.audio

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object AudioPermissionHelper {

    private const val TAG = "AudioPermissionHelper"
    const val REQUEST_CODE_AUDIO_PERMISSION = 1001
    const val REQUEST_CODE_BLUETOOTH_PERMISSION = 1002

    

    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    

    fun shouldShowRationale(activity: Activity): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO
        )
    }

    

    fun requestAudioPermission(activity: Activity) {
        Log.i(TAG, "Requesting RECORD_AUDIO permission")

        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE_AUDIO_PERMISSION
        )
    }

    

    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit,
        onPermanentlyDenied: (() -> Unit)? = null
    ) {
        if (requestCode != REQUEST_CODE_AUDIO_PERMISSION) {
            return
        }

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Audio permission granted")
            onGranted()
        } else {
            Log.w(TAG, "Audio permission denied")

            
            if (permissions.isNotEmpty() && permissions[0] == Manifest.permission.RECORD_AUDIO) {
                
                
                if (onPermanentlyDenied != null) {
                    
                    onDenied()
                } else {
                    onDenied()
                }
            }
        }
    }

    

    fun getPermissionExplanation(showExtendedHelp: Boolean = false): String {
        return if (showExtendedHelp) {
            """
            To make voice calls, this app needs permission to access your microphone.

            TV Setup:
            • If your TV doesn't have a built-in microphone, you can:
              - Connect a USB microphone
              - Connect a Bluetooth headset/microphone
              - Use a wired headset with microphone

            • Once connected, grant microphone permission to enable 2-way calling

            Without a microphone:
            • You can still receive calls
            • You'll be able to hear the caller
            • But they won't be able to hear you
            """.trimIndent()
        } else {
            "Microphone permission is required to make voice calls. Without it, you can only receive calls (listen-only mode)."
        }
    }

    

    fun getPermanentlyDeniedMessage(): String {
        return """
            Microphone permission was permanently denied.

            To enable voice calls:
            1. Open your TV's Settings
            2. Go to Apps
            3. Find "TV Caller App"
            4. Go to Permissions
            5. Enable "Microphone" permission

            Without microphone permission, you can only receive calls in listen-only mode.
        """.trimIndent()
    }

    

    fun needsBluetoothPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }

    

    fun getBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            emptyArray()
        }
    }

    

    fun hasBluetoothPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true 
    }
}
