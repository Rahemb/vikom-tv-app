package com.example.tv_caller_app.calling.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.tv_caller_app.calling.audio.AudioDeviceDetector
import com.example.tv_caller_app.datasource.MicrophoneMode
import com.example.tv_caller_app.datasource.WebRTCDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.webrtc.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WebRTCManager(private val context: Context) : WebRTCDataSource {

    companion object {
        private const val TAG = "WebRTCManager"
        const val DEBUG_DISABLE_AUDIO = false
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioDeviceDetector = AudioDeviceDetector(context)

    private var isInitialized = false
    private var isMuted = false
    private var isSpeakerOn = false
    private var savedVolume: Int = -1

    private var currentMicrophoneMode = MicrophoneMode.RECEIVE_ONLY

    sealed class ConnectionState {
        object Idle : ConnectionState()
        object Connecting : ConnectionState()
        object Connected : ConnectionState()
        object Disconnected : ConnectionState()
        object Failed : ConnectionState()
        object Closed : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)

    override var onIceCandidate: ((IceCandidate) -> Unit)? = null
    override var onConnectionStateChange: ((PeerConnection.IceConnectionState) -> Unit)? = null
    var onRemoteStream: ((MediaStream) -> Unit)? = null
    override var onMicrophoneModeChanged: ((MicrophoneMode, String) -> Unit)? = null

    override fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "WebRTC already initialized")
            return
        }

        if (DEBUG_DISABLE_AUDIO) {
            Log.w(TAG, "⚠️ DEBUG MODE: Audio disabled for emulator testing")
            Log.w(TAG, "⚠️ Signaling will work but no audio will be transmitted")
        }

        Log.i(TAG, "Initializing WebRTC...")

        try {
            if (DEBUG_DISABLE_AUDIO) {
                currentMicrophoneMode = MicrophoneMode.RECEIVE_ONLY
                Log.w(TAG, "Debug mode: Microphone disabled")
                onMicrophoneModeChanged?.invoke(currentMicrophoneMode, "Debug mode - audio disabled")
            } else {
                val micStatus = audioDeviceDetector.checkMicrophoneAvailability()
                currentMicrophoneMode = if (micStatus.isAvailable && micStatus.hasPermission) {
                    Log.i(TAG, "Microphone available: ${micStatus.deviceName} (${micStatus.deviceType})")
                    MicrophoneMode.TWO_WAY
                } else {
                    Log.w(TAG, "Microphone not available: ${micStatus.message}")
                    MicrophoneMode.RECEIVE_ONLY
                }
                onMicrophoneModeChanged?.invoke(currentMicrophoneMode, micStatus.message)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    audioDeviceDetector.registerAudioDeviceCallback(object : AudioDeviceDetector.MicrophoneDeviceCallback {
                        override fun onMicrophoneConnected(type: AudioDeviceDetector.MicrophoneType, deviceName: String?) {
                            Log.i(TAG, "Microphone connected during call: $type — $deviceName")
                            recheckMicrophoneAvailability()
                        }
                        override fun onMicrophoneDisconnected(type: AudioDeviceDetector.MicrophoneType) {
                            Log.w(TAG, "Microphone disconnected during call: $type")
                            recheckMicrophoneAvailability()
                        }
                    })
                }
            }

            if (DEBUG_DISABLE_AUDIO) {
                val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(false)
                    .createInitializationOptions()

                PeerConnectionFactory.initialize(initOptions)

                val options = PeerConnectionFactory.Options()
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setAudioDeviceModule(null)
                    .createPeerConnectionFactory()
            } else {
                val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                    .createInitializationOptions()

                PeerConnectionFactory.initialize(initOptions)

                val options = PeerConnectionFactory.Options()
                peerConnectionFactory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .createPeerConnectionFactory()
            }

            isInitialized = true
            Log.i(TAG, "WebRTC initialized successfully in $currentMicrophoneMode mode")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
            throw e
        }
    }

    fun recheckMicrophoneAvailability(): MicrophoneMode {
        val micStatus = audioDeviceDetector.checkMicrophoneAvailability()
        val newMode = if (micStatus.isAvailable && micStatus.hasPermission) {
            MicrophoneMode.TWO_WAY
        } else {
            MicrophoneMode.RECEIVE_ONLY
        }

        if (newMode != currentMicrophoneMode) {
            Log.i(TAG, "Microphone mode changed: $currentMicrophoneMode -> $newMode")
            currentMicrophoneMode = newMode
            onMicrophoneModeChanged?.invoke(currentMicrophoneMode, micStatus.message)
        }

        return currentMicrophoneMode
    }

    private fun createAudioTrack(): AudioTrack? {
        if (DEBUG_DISABLE_AUDIO) {
            Log.w(TAG, "Debug mode: Skipping audio track creation")
            return null
        }

        if (currentMicrophoneMode == MicrophoneMode.RECEIVE_ONLY) {
            Log.w(TAG, "Skipping audio track creation - RECEIVE_ONLY mode (no microphone)")
            return null
        }

        try {
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", WebRTCConfig.AudioConstraints.ECHO_CANCELLATION.toString()))
                mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", WebRTCConfig.AudioConstraints.AUTO_GAIN_CONTROL.toString()))
                mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", WebRTCConfig.AudioConstraints.HIGH_PASS_FILTER.toString()))
                mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", WebRTCConfig.AudioConstraints.NOISE_SUPPRESSION.toString()))
                mandatory.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", WebRTCConfig.AudioConstraints.TYPED_NOISE_DETECTION.toString()))
            }

            audioSource = peerConnectionFactory?.createAudioSource(constraints)
            localAudioTrack = peerConnectionFactory?.createAudioTrack(
                WebRTCConfig.StreamLabels.AUDIO_TRACK_ID,
                audioSource
            )

            Log.d(TAG, "Audio track created successfully")
            return localAudioTrack

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create audio track", e)
            currentMicrophoneMode = MicrophoneMode.RECEIVE_ONLY
            onMicrophoneModeChanged?.invoke(
                currentMicrophoneMode,
                "Failed to access microphone. Switched to receive-only mode."
            )
            return null
        }
    }

    private fun createPeerConnection(): PeerConnection? {
        try {
            val rtcConfig = WebRTCConfig.createRTCConfiguration()

            val observer = PeerConnectionObserver(
                onIceCandidateCallback = { candidate ->
                    Log.d(TAG, "ICE candidate generated: ${candidate.sdp}")
                    onIceCandidate?.invoke(candidate)
                },
                onConnectionChangeCallback = { state ->
                    Log.i(TAG, "ICE connection state: $state")

                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            _connectionState.value = ConnectionState.Connected
                        }
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            _connectionState.value = ConnectionState.Disconnected
                        }
                        PeerConnection.IceConnectionState.FAILED -> {
                            _connectionState.value = ConnectionState.Failed
                        }
                        PeerConnection.IceConnectionState.CLOSED -> {
                            _connectionState.value = ConnectionState.Closed
                        }
                        else -> {
                            _connectionState.value = ConnectionState.Connecting
                        }
                    }

                    onConnectionStateChange?.invoke(state)
                },
                onAddStreamCallback = { stream ->
                    Log.i(TAG, "Remote stream added")

                    if (stream.audioTracks.isNotEmpty()) {
                        remoteAudioTrack = stream.audioTracks[0]
                        remoteAudioTrack?.setEnabled(true)
                        Log.d(TAG, "Remote audio track enabled")
                    }

                    onRemoteStream?.invoke(stream)
                },
                onRemoveStreamCallback = { stream ->
                    Log.i(TAG, "Remote stream removed")
                    remoteAudioTrack = null
                }
            )

            peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)
            Log.d(TAG, "Peer connection created")

            return peerConnection

        } catch (e: Exception) {
            Log.e(TAG, "Failed to create peer connection", e)
            return null
        }
    }

    override suspend fun createOffer(): String = suspendCoroutine { continuation ->
        try {
            if (!isInitialized) {
                throw IllegalStateException("WebRTC not initialized")
            }

            if (DEBUG_DISABLE_AUDIO) {
                Log.w(TAG, "Debug mode: Creating offer WITHOUT audio")
            }

            Log.i(TAG, "Creating offer in $currentMicrophoneMode mode...")
            _connectionState.value = ConnectionState.Connecting

            val pc = createPeerConnection()
                ?: throw RuntimeException("Failed to create peer connection")

            val audioTrack = createAudioTrack()

            if (audioTrack != null) {
                val streamId = WebRTCConfig.StreamLabels.STREAM_ID
                pc.addTrack(audioTrack, listOf(streamId))
                Log.d(TAG, "Local audio track added to peer connection (Unified Plan)")
            } else {
                Log.w(TAG, "No local audio track - RECEIVE_ONLY mode")
            }

            
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    sdp?.let {
                        Log.d(TAG, "Offer created successfully")

                        
                        val modifiedSdp = it

                        
                        pc.setLocalDescription(object : SdpObserver {
                            override fun onSetSuccess() {
                                Log.d(TAG, "Local description set")
                                continuation.resume(modifiedSdp.description)
                            }

                            override fun onSetFailure(error: String?) {
                                Log.e(TAG, "Set local description failed: $error")
                                continuation.resumeWith(Result.failure(RuntimeException(error)))
                            }

                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onCreateFailure(p0: String?) {}
                        }, it)
                    } ?: run {
                        continuation.resumeWith(Result.failure(RuntimeException("SDP is null")))
                    }
                }

                override fun onCreateFailure(error: String?) {
                    Log.e(TAG, "Create offer failed: $error")
                    continuation.resumeWith(Result.failure(RuntimeException(error)))
                }

                override fun onSetSuccess() {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)

        } catch (e: Exception) {
            Log.e(TAG, "Create offer exception", e)
            continuation.resumeWith(Result.failure(e))
        }
    }

    

    override suspend fun createAnswer(offerSdp: String): String = suspendCoroutine { continuation ->
        try {
            if (!isInitialized) {
                throw IllegalStateException("WebRTC not initialized")
            }

            if (DEBUG_DISABLE_AUDIO) {
                Log.w(TAG, "Debug mode: Creating answer WITHOUT audio")
            }

            Log.i(TAG, "Creating answer in $currentMicrophoneMode mode...")
            _connectionState.value = ConnectionState.Connecting

            
            val pc = createPeerConnection()
                ?: throw RuntimeException("Failed to create peer connection")

            val audioTrack = createAudioTrack()

            if (audioTrack != null) {
                val streamId = WebRTCConfig.StreamLabels.STREAM_ID
                pc.addTrack(audioTrack, listOf(streamId))
                Log.d(TAG, "Local audio track added to peer connection (Unified Plan)")
            } else {
                Log.w(TAG, "No local audio track - RECEIVE_ONLY mode")
            }

            
            val remoteDescription = SessionDescription(SessionDescription.Type.OFFER, offerSdp)

            pc.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description (offer) set successfully")

                    
                    val constraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
                    }

                    pc.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            sdp?.let {
                                Log.d(TAG, "Answer created successfully")

                                
                                val modifiedSdp = it

                                
                                pc.setLocalDescription(object : SdpObserver {
                                    override fun onSetSuccess() {
                                        Log.d(TAG, "Local description (answer) set")
                                        continuation.resume(modifiedSdp.description)
                                    }

                                    override fun onSetFailure(error: String?) {
                                        Log.e(TAG, "Set local description (answer) failed: $error")
                                        continuation.resumeWith(Result.failure(RuntimeException(error)))
                                    }

                                    override fun onCreateSuccess(p0: SessionDescription?) {}
                                    override fun onCreateFailure(p0: String?) {}
                                }, it)
                            } ?: run {
                                continuation.resumeWith(Result.failure(RuntimeException("Answer SDP is null")))
                            }
                        }

                        override fun onCreateFailure(error: String?) {
                            Log.e(TAG, "Create answer failed: $error")
                            continuation.resumeWith(Result.failure(RuntimeException(error)))
                        }

                        override fun onSetSuccess() {}
                        override fun onSetFailure(p0: String?) {}
                    }, constraints)
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set remote description (offer) failed: $error")
                    continuation.resumeWith(Result.failure(RuntimeException(error)))
                }

                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDescription)

        } catch (e: Exception) {
            Log.e(TAG, "Create answer exception", e)
            continuation.resumeWith(Result.failure(e))
        }
    }

    

    override suspend fun handleAnswer(answerSdp: String): Result<Unit> = suspendCoroutine { continuation ->
        try {
            Log.i(TAG, "Handling answer...")

            val remoteDescription = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)

            peerConnection?.setRemoteDescription(object : SdpObserver {
                override fun onSetSuccess() {
                    Log.d(TAG, "Remote description (answer) set successfully")
                    continuation.resume(Result.success(Unit))
                }

                override fun onSetFailure(error: String?) {
                    Log.e(TAG, "Set remote description (answer) failed: $error")
                    continuation.resume(Result.failure(RuntimeException(error)))
                }

                override fun onCreateSuccess(p0: SessionDescription?) {}
                override fun onCreateFailure(p0: String?) {}
            }, remoteDescription)

        } catch (e: Exception) {
            Log.e(TAG, "Handle answer exception", e)
            continuation.resume(Result.failure(e))
        }
    }

    

    override fun addIceCandidate(candidateSdp: String, sdpMid: String?, sdpMLineIndex: Int?) {
        try {
            val iceCandidate = IceCandidate(sdpMid, sdpMLineIndex ?: 0, candidateSdp)

            peerConnection?.addIceCandidate(iceCandidate)
            Log.d(TAG, "ICE candidate added")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to add ICE candidate", e)
        }
    }

    

    override fun mute() {
        if (currentMicrophoneMode == MicrophoneMode.RECEIVE_ONLY) {
            Log.w(TAG, "Cannot mute - RECEIVE_ONLY mode (no microphone)")
            return
        }

        localAudioTrack?.setEnabled(false)
        isMuted = true
        Log.i(TAG, "Audio muted")
    }

    

    override fun unmute() {
        if (currentMicrophoneMode == MicrophoneMode.RECEIVE_ONLY) {
            Log.w(TAG, "Cannot unmute - RECEIVE_ONLY mode (no microphone)")
            return
        }

        localAudioTrack?.setEnabled(true)
        isMuted = false
        Log.i(TAG, "Audio unmuted")
    }

    

    override fun enableSpeaker() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val speaker = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }

            if (speaker != null) {
                val result = audioManager.setCommunicationDevice(speaker)
                Log.i(TAG, "Speaker enabled (modern API), result=$result")
            } else {
                Log.w(TAG, "Built-in speaker not found in available devices")
            }

            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = true
            Log.i(TAG, "Speaker enabled (legacy API)")
        }

        
        val voiceStream = AudioManager.STREAM_VOICE_CALL
        savedVolume = audioManager.getStreamVolume(voiceStream)
        val maxVolume = audioManager.getStreamMaxVolume(voiceStream)
        audioManager.setStreamVolume(voiceStream, maxVolume, 0)
        Log.i(TAG, "Speaker volume boosted: $savedVolume -> $maxVolume")

        isSpeakerOn = true
    }

    

    override fun disableSpeaker() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val earpiece = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }

            if (earpiece != null) {
                val result = audioManager.setCommunicationDevice(earpiece)
                Log.i(TAG, "Speaker disabled - routed to earpiece (modern API), result=$result")
            } else {
                audioManager.clearCommunicationDevice()
                Log.i(TAG, "Speaker disabled - no earpiece found, cleared device (modern API)")
            }

            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } else {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            Log.i(TAG, "Speaker disabled (legacy API)")
        }

        
        if (savedVolume >= 0) {
            val voiceStream = AudioManager.STREAM_VOICE_CALL
            audioManager.setStreamVolume(voiceStream, savedVolume, 0)
            Log.i(TAG, "Speaker volume restored to $savedVolume")
            savedVolume = -1
        }

        isSpeakerOn = false
    }

    

    override suspend fun close() {
        Log.i(TAG, "Closing WebRTC connection...")

        try {
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioDeviceDetector.unregisterAudioDeviceCallback()
            }

            
            localAudioTrack?.setEnabled(false)
            remoteAudioTrack?.setEnabled(false)

            
            peerConnection?.let { pc ->
                try {
                    
                    pc.senders?.forEach { sender ->
                        try {
                            pc.removeTrack(sender)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error removing sender", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error removing senders", e)
                }
            }

            
            peerConnection?.close()

            
            
            delay(200)

            
            try {
                localAudioTrack?.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "Error disposing local audio track", e)
            }

            try {
                remoteAudioTrack?.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "Error disposing remote audio track", e)
            }

            try {
                audioSource?.dispose()
            } catch (e: Exception) {
                Log.w(TAG, "Error disposing audio source", e)
            }

            
            
            

            
            localAudioTrack = null
            remoteAudioTrack = null
            audioSource = null
            peerConnection = null
            isMuted = false
            isSpeakerOn = false

            _connectionState.value = ConnectionState.Closed

            Log.i(TAG, "WebRTC connection closed")

        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebRTC connection", e)
        }
    }

    

    override fun cleanup() {
        Log.i(TAG, "Cleaning up WebRTC...")

        
        runBlocking {
            close()
        }

        try {
            peerConnectionFactory?.dispose()
            peerConnectionFactory = null
            isInitialized = false

            PeerConnectionFactory.stopInternalTracingCapture()
            PeerConnectionFactory.shutdownInternalTracer()

            Log.i(TAG, "WebRTC cleanup complete")

        } catch (e: Exception) {
            Log.e(TAG, "Error during WebRTC cleanup", e)
        }
    }
}
