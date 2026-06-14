package com.example.tv_caller_app.datasource

import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

enum class MicrophoneMode {
    TWO_WAY,
    RECEIVE_ONLY
}

interface WebRTCDataSource {
    var onIceCandidate: ((IceCandidate) -> Unit)?
    var onConnectionStateChange: ((PeerConnection.IceConnectionState) -> Unit)?
    var onMicrophoneModeChanged: ((MicrophoneMode, String) -> Unit)?

    fun initialize()
    suspend fun createOffer(): String
    suspend fun createAnswer(offerSdp: String): String
    suspend fun handleAnswer(answerSdp: String): Result<Unit>
    fun addIceCandidate(candidateSdp: String, sdpMid: String?, sdpMLineIndex: Int?)
    fun mute()
    fun unmute()
    fun enableSpeaker()
    fun disableSpeaker()
    suspend fun close()
    fun cleanup()
}
