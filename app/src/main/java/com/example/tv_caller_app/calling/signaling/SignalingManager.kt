package com.example.tv_caller_app.calling.signaling

import android.util.Log
import com.example.tv_caller_app.datasource.SignalingService
import com.example.tv_caller_app.network.SupabaseClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class SignalingManager(
    private val currentUserId: String
) : SignalingService {
    private val supabase = SupabaseClient.client

    companion object {
        private const val TAG = "SignalingManager"
        private const val SUBSCRIBE_TIMEOUT_MS = 15_000L
    }
    private val json = Json { ignoreUnknownKeys = true }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentUserContactId: String? = null
    private var signalingChannel: RealtimeChannel? = null
    private val _events = MutableSharedFlow<SignalingEvent>(replay = 0, extraBufferCapacity = 10)
    override val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()
    private var isInitialized = false

    override suspend fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "SignalingManager already initialized")
            return
        }

        try {
            Log.d(TAG, "Initializing SignalingManager for user: $currentUserId")

            try {
                @Serializable
                data class ContactIdRow(
                    @SerialName("contact_id") val contactId: Int
                )

                val rows = supabase.from("profiles")
                    .select(columns = io.github.jan.supabase.postgrest.query.Columns.list("contact_id")) {
                        filter {
                            eq("id", currentUserId)
                        }
                    }
                    .decodeList<ContactIdRow>()

                currentUserContactId = rows.firstOrNull()?.contactId?.toString()
                Log.i(TAG, "User contact_id fetched: $currentUserContactId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch user contact_id, using UUID as fallback", e)
                currentUserContactId = currentUserId
            }

            signalingChannel = supabase.channel("webrtc-signaling")

            signalingChannel?.let { channel ->
                channel.broadcastFlow<JsonObject>(event = "message")
                    .onEach { messageJson ->
                        handleIncomingMessage(messageJson)
                    }
                    .launchIn(scope)

                // Block until the channel actually reaches SUBSCRIBED instead of logging
                // success right after the non-blocking subscribe() call. The Realtime
                // socket retries forever on a dead network, so guard with a timeout —
                // an offline device then surfaces as an Error rather than a silent
                // "Connected" that never receives broadcasts.
                try {
                    withTimeout(SUBSCRIBE_TIMEOUT_MS) {
                        channel.subscribe(blockUntilSubscribed = true)
                    }

                    Log.i(TAG, "✅ Subscribed to shared signaling channel for user: $currentUserId")
                    _events.emit(SignalingEvent.Connected)
                    isInitialized = true
                } catch (e: TimeoutCancellationException) {
                    Log.e(TAG, "❌ Signaling channel did not reach SUBSCRIBED within ${SUBSCRIBE_TIMEOUT_MS}ms — check device connectivity", e)
                    channel.unsubscribe()
                    signalingChannel = null
                    _events.emit(SignalingEvent.Error("Signaling channel did not connect (check network)", e))
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SignalingManager", e)
            _events.emit(SignalingEvent.Error("Failed to initialize signaling", e))
        }
    }

    override suspend fun sendCallOffer(
        targetUserId: String,
        callerName: String,
        callerUsername: String,
        offer: String,
        mediaType: String
    ) {
        try {
            Log.d(TAG, "Sending call offer to: $targetUserId")

            val message = SignalingMessage.CallOffer(
                callerId = currentUserContactId ?: currentUserId,
                callerUserId = currentUserId,
                callerName = callerName,
                callerUsername = callerUsername,
                sdp = offer,
                mediaType = mediaType
            )

            val payload = buildJsonObject {
                put("targetUserId", targetUserId)
                put("type", "call_offer")
                put("payload", json.encodeToString(message))
            }

            Log.d(TAG, "📤 Broadcasting offer to shared channel - Target: $targetUserId")

            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.i(TAG, "✅ Call offer broadcast completed")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send call offer", e)
            _events.emit(SignalingEvent.Error("Failed to send call offer", e))
        }
    }

    override suspend fun sendCallAnswer(
        callerId: String,
        calleeName: String,
        answer: String
    ) {
        try {
            Log.d(TAG, "Sending call answer to: $callerId")

            val message = SignalingMessage.CallAnswer(
                calleeId = currentUserId,
                calleeName = calleeName,
                sdp = answer
            )

            val payload = buildJsonObject {
                put("targetUserId", callerId)
                put("type", "call_answer")
                put("payload", json.encodeToString(message))
            }

            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.i(TAG, "Call answer sent to: $callerId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send call answer", e)
            _events.emit(SignalingEvent.Error("Failed to send call answer", e))
        }
    }

    

    override suspend fun sendIceCandidate(
        targetUserId: String,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int?
    ) {
        try {
            
            val message = SignalingMessage.IceCandidate(
                candidate = candidate,
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex
            )

            val payload = buildJsonObject {
                put("targetUserId", targetUserId)
                put("type", "ice_candidate")
                put("payload", json.encodeToString(message))
            }

            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.d(TAG, "ICE candidate sent to: $targetUserId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send ICE candidate", e)
        }
    }

    

    override suspend fun rejectCall(callerId: String, reason: String) {
        try {
            Log.d(TAG, "Rejecting call from: $callerId")

            
            val messageStr = json.encodeToString(SignalingMessage.CallRejected(reason = reason))
            Log.d(TAG, "Rejection payload: $messageStr")

            val payload = buildJsonObject {
                put("targetUserId", callerId)
                put("type", "call_rejected")
                put("payload", messageStr)
            }

            Log.d(TAG, "📤 Broadcasting rejection: $payload")

            
            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.i(TAG, "Call rejection sent to: $callerId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to reject call", e)
        }
    }

    

    override suspend fun endCall(otherUserId: String, reason: String, duration: Long) {
        try {
            Log.d(TAG, "Ending call with: $otherUserId")

            
            val message = SignalingMessage.CallEnded(
                reason = reason,
                duration = duration
            )

            val payload = buildJsonObject {
                put("targetUserId", otherUserId)
                put("type", "call_ended")
                put("payload", json.encodeToString(message))
            }

            signalingChannel?.broadcast(
                event = "message",
                message = payload
            )

            Log.i(TAG, "Call end sent to: $otherUserId")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call", e)
        }
    }

    

    private fun handleIncomingMessage(messageJsonObject: JsonObject) {
        scope.launch {
            try {
                Log.d(TAG, "📨 Raw broadcast received: $messageJsonObject")

                
                val targetUserId = messageJsonObject["targetUserId"]?.jsonPrimitive?.contentOrNull

                if (targetUserId != null && targetUserId != currentUserId) {
                    Log.d(TAG, "⏭️ Message not for me (target: $targetUserId, me: $currentUserId) - ignoring")
                    return@launch
                }

                
                val messageType = messageJsonObject["type"]?.jsonPrimitive?.contentOrNull
                val messageJson = messageJsonObject["payload"]?.jsonPrimitive?.contentOrNull ?: ""

                Log.d(TAG, "📨 Type: $messageType, Payload for me: $messageJson")

                
                when (messageType) {
                    "call_offer" -> {
                        val msg = json.decodeFromString<SignalingMessage.CallOffer>(messageJson)
                        _events.emit(
                            SignalingEvent.IncomingCall(
                                callerId = msg.callerId,
                                callerUserId = msg.callerUserId,
                                callerName = msg.callerName,
                                callerUsername = msg.callerUsername,
                                offer = msg.sdp,
                                mediaType = msg.mediaType
                            )
                        )
                    }
                    "call_answer" -> {
                        val msg = json.decodeFromString<SignalingMessage.CallAnswer>(messageJson)
                        _events.emit(SignalingEvent.CallAnswered(answer = msg.sdp))
                    }
                    "ice_candidate" -> {
                        val msg = json.decodeFromString<SignalingMessage.IceCandidate>(messageJson)
                        _events.emit(
                            SignalingEvent.NewIceCandidate(
                                candidate = msg.candidate,
                                sdpMid = msg.sdpMid,
                                sdpMLineIndex = msg.sdpMLineIndex
                            )
                        )
                    }
                    "call_rejected" -> {
                        val msg = json.decodeFromString<SignalingMessage.CallRejected>(messageJson)
                        _events.emit(SignalingEvent.CallRejected(reason = msg.reason))
                    }
                    "call_ended" -> {
                        val msg = json.decodeFromString<SignalingMessage.CallEnded>(messageJson)
                        _events.emit(SignalingEvent.CallEnded(reason = msg.reason, duration = msg.duration))
                    }
                    "appointment_event" -> {
                        val msg = json.decodeFromString<SignalingMessage.AppointmentEvent>(messageJson)
                        _events.emit(
                            SignalingEvent.AppointmentEvent(
                                appointmentId = msg.appointmentId,
                                action = msg.action,
                                date = msg.date,
                                startTime = msg.startTime,
                                endTime = msg.endTime,
                                personnelName = msg.personnelName,
                                status = msg.status,
                                shortMessage = msg.shortMessage
                            )
                        )
                    }
                    else -> {
                        
                        Log.w(TAG, "No/unknown type '$messageType', attempting field-based parsing: $messageJson")
                        when {
                            "callerUserId" in messageJson && "sdp" in messageJson -> {
                                val msg = json.decodeFromString<SignalingMessage.CallOffer>(messageJson)
                                _events.emit(SignalingEvent.IncomingCall(msg.callerId, msg.callerUserId, msg.callerName, msg.callerUsername, msg.sdp, msg.mediaType))
                            }
                            "calleeId" in messageJson -> {
                                val msg = json.decodeFromString<SignalingMessage.CallAnswer>(messageJson)
                                _events.emit(SignalingEvent.CallAnswered(answer = msg.sdp))
                            }
                            "candidate" in messageJson -> {
                                val msg = json.decodeFromString<SignalingMessage.IceCandidate>(messageJson)
                                _events.emit(SignalingEvent.NewIceCandidate(msg.candidate, msg.sdpMid, msg.sdpMLineIndex))
                            }
                            "duration" in messageJson -> {
                                val msg = json.decodeFromString<SignalingMessage.CallEnded>(messageJson)
                                _events.emit(SignalingEvent.CallEnded(reason = msg.reason, duration = msg.duration))
                            }
                            "reason" in messageJson -> {
                                val msg = json.decodeFromString<SignalingMessage.CallRejected>(messageJson)
                                _events.emit(SignalingEvent.CallRejected(reason = msg.reason))
                            }
                            "appointmentId" in messageJson -> {
                                val msg = json.decodeFromString<SignalingMessage.AppointmentEvent>(messageJson)
                                _events.emit(
                                    SignalingEvent.AppointmentEvent(
                                        appointmentId = msg.appointmentId,
                                        action = msg.action,
                                        date = msg.date,
                                        startTime = msg.startTime,
                                        endTime = msg.endTime,
                                        personnelName = msg.personnelName,
                                        status = msg.status,
                                        shortMessage = msg.shortMessage
                                    )
                                )
                            }
                            else -> Log.w(TAG, "Unknown message: $messageJson")
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle incoming message", e)
                _events.emit(SignalingEvent.Error("Failed to parse signaling message", e))
            }
        }
    }

    

    override suspend fun cleanup() {
        try {
            Log.d(TAG, "Cleaning up SignalingManager")

            signalingChannel?.unsubscribe()
            signalingChannel = null
            isInitialized = false

            _events.emit(SignalingEvent.Disconnected)

            
            scope.cancel()

            Log.i(TAG, "SignalingManager cleaned up")

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
