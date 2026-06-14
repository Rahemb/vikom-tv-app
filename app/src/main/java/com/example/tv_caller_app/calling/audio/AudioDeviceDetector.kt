package com.example.tv_caller_app.calling.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

class AudioDeviceDetector(private val context: Context) {

    companion object {
        private const val TAG = "AudioDeviceDetector"
    }

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    
    private var audioDeviceCallbackHolder: Any? = null

    

    data class MicrophoneStatus(
        val isAvailable: Boolean,
        val hasPermission: Boolean,
        val deviceType: MicrophoneType,
        val deviceName: String?,
        val message: String
    )

    

    enum class MicrophoneType {
        NONE,           
        BUILT_IN,       
        USB,            
        BLUETOOTH,      
        WIRED_HEADSET   
    }

    

    interface MicrophoneDeviceCallback {
        fun onMicrophoneConnected(type: MicrophoneType, deviceName: String?)
        fun onMicrophoneDisconnected(type: MicrophoneType)
    }

    

    fun checkMicrophoneAvailability(): MicrophoneStatus {
        
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.w(TAG, "RECORD_AUDIO permission not granted")
            return MicrophoneStatus(
                isAvailable = false,
                hasPermission = false,
                deviceType = MicrophoneType.NONE,
                deviceName = null,
                message = "Microphone permission required. Please grant permission in settings."
            )
        }

        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

            
            val bluetoothMic = devices.firstOrNull { isBluetoothMic(it) }
            if (bluetoothMic != null) {
                Log.i(TAG, "Bluetooth microphone detected: ${bluetoothMic.productName}")
                return MicrophoneStatus(
                    isAvailable = true,
                    hasPermission = true,
                    deviceType = MicrophoneType.BLUETOOTH,
                    deviceName = bluetoothMic.productName?.toString() ?: "Bluetooth Microphone",
                    message = "Bluetooth microphone connected and ready"
                )
            }

            val usbMic = devices.firstOrNull { isUSBMic(it) }
            if (usbMic != null) {
                Log.i(TAG, "USB microphone detected: ${usbMic.productName}")
                return MicrophoneStatus(
                    isAvailable = true,
                    hasPermission = true,
                    deviceType = MicrophoneType.USB,
                    deviceName = usbMic.productName?.toString() ?: "USB Microphone",
                    message = "USB microphone connected and ready"
                )
            }

            val wiredHeadset = devices.firstOrNull { isWiredHeadsetMic(it) }
            if (wiredHeadset != null) {
                Log.i(TAG, "Wired headset microphone detected")
                return MicrophoneStatus(
                    isAvailable = true,
                    hasPermission = true,
                    deviceType = MicrophoneType.WIRED_HEADSET,
                    deviceName = "Wired Headset",
                    message = "Wired headset microphone connected and ready"
                )
            }

            val builtInMic = devices.firstOrNull { isBuiltInMic(it) }
            if (builtInMic != null) {
                Log.i(TAG, "Built-in microphone detected")
                return MicrophoneStatus(
                    isAvailable = true,
                    hasPermission = true,
                    deviceType = MicrophoneType.BUILT_IN,
                    deviceName = "Built-in Microphone",
                    message = "Built-in microphone ready"
                )
            }

            
            Log.w(TAG, "No microphone detected on device")
            return MicrophoneStatus(
                isAvailable = false,
                hasPermission = true,
                deviceType = MicrophoneType.NONE,
                deviceName = null,
                message = "No microphone detected. Please connect a USB or Bluetooth microphone to make calls."
            )
        } else {
            
            Log.i(TAG, "Running on Android < M, assuming microphone available")
            return MicrophoneStatus(
                isAvailable = true,
                hasPermission = true,
                deviceType = MicrophoneType.BUILT_IN,
                deviceName = "Microphone",
                message = "Microphone ready"
            )
        }
    }

    

    fun isBluetoothMicConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return devices.any { isBluetoothMic(it) }
        }
        return false
    }

    

    fun isUSBMicConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            return devices.any { isUSBMic(it) }
        }
        return false
    }

    

    fun getAvailableMicrophones(): List<String> {
        val micList = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)

            devices.forEach { device ->
                if (device.isSink) return@forEach 

                val deviceName = when {
                    isBluetoothMic(device) -> "Bluetooth: ${device.productName}"
                    isUSBMic(device) -> "USB: ${device.productName}"
                    isWiredHeadsetMic(device) -> "Wired Headset"
                    isBuiltInMic(device) -> "Built-in Microphone"
                    else -> "Unknown: ${device.productName} (type=${device.type})"
                }

                micList.add(deviceName)
            }
        }

        return micList
    }

    

    @RequiresApi(Build.VERSION_CODES.M)
    fun registerAudioDeviceCallback(callback: MicrophoneDeviceCallback) {
        unregisterAudioDeviceCallback() 

        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                addedDevices.forEach { device ->
                    val type = micTypeOf(device) ?: return@forEach
                    val name = device.productName?.toString()
                    Log.i(TAG, "Microphone connected: $type - $name")
                    callback.onMicrophoneConnected(type, name)
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                removedDevices.forEach { device ->
                    val type = micTypeOf(device) ?: return@forEach
                    Log.i(TAG, "Microphone disconnected: $type")
                    callback.onMicrophoneDisconnected(type)
                }
            }
        }

        audioDeviceCallbackHolder = cb
        audioManager.registerAudioDeviceCallback(cb, null)
        Log.d(TAG, "Audio device callback registered")
    }

    

    @RequiresApi(Build.VERSION_CODES.M)
    fun unregisterAudioDeviceCallback() {
        val cb = audioDeviceCallbackHolder as? AudioDeviceCallback ?: return
        audioManager.unregisterAudioDeviceCallback(cb)
        audioDeviceCallbackHolder = null
        Log.d(TAG, "Audio device callback unregistered")
    }

    

    @RequiresApi(Build.VERSION_CODES.M)
    private fun micTypeOf(device: AudioDeviceInfo): MicrophoneType? = when {
        isBluetoothMic(device) -> MicrophoneType.BLUETOOTH
        isUSBMic(device) -> MicrophoneType.USB
        isWiredHeadsetMic(device) -> MicrophoneType.WIRED_HEADSET
        isBuiltInMic(device) -> MicrophoneType.BUILT_IN
        else -> null
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isBluetoothMic(device: AudioDeviceInfo): Boolean {
        return device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
               device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isUSBMic(device: AudioDeviceInfo): Boolean {
        if (device.type == AudioDeviceInfo.TYPE_USB_DEVICE) return true
        if (device.type == AudioDeviceInfo.TYPE_USB_ACCESSORY) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            device.type == AudioDeviceInfo.TYPE_USB_HEADSET) return true
        return false
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isWiredHeadsetMic(device: AudioDeviceInfo): Boolean {
        return device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun isBuiltInMic(device: AudioDeviceInfo): Boolean {
        return device.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
    }
}
