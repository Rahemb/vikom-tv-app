package com.example.tv_caller_app.datasource

import com.example.tv_caller_app.calling.signaling.SignalingEvent
import kotlinx.coroutines.flow.SharedFlow

interface SignalingService {
    val events: SharedFlow<SignalingEvent>
    suspend fun initialize()
    suspend fun sendCallOffer(targetUserId: String, callerName: String, callerUsername: String, offer: String, mediaType: String = "audio")
    suspend fun sendCallAnswer(callerId: String, calleeName: String, answer: String)
    suspend fun sendIceCandidate(targetUserId: String, candidate: String, sdpMid: String?, sdpMLineIndex: Int?)
    suspend fun rejectCall(callerId: String, reason: String = "user_declined")
    suspend fun endCall(otherUserId: String, reason: String, duration: Long = 0)
    suspend fun cleanup()
}
