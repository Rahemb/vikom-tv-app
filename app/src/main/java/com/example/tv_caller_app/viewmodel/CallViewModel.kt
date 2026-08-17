package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.datasource.AppointmentDataSource
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.calling.signaling.SignalingEvent
import com.example.tv_caller_app.datasource.CallHistoryDataSource
import com.example.tv_caller_app.datasource.CallNotificationDataSource
import com.example.tv_caller_app.datasource.ContactDataSource
import com.example.tv_caller_app.datasource.FCMDataSource
import com.example.tv_caller_app.datasource.MicrophoneMode
import com.example.tv_caller_app.datasource.PresenceDataSource
import com.example.tv_caller_app.datasource.QuickDialDataSource
import com.example.tv_caller_app.datasource.SignalingService
import com.example.tv_caller_app.datasource.WebRTCDataSource
import com.example.tv_caller_app.model.CallHistoryInsert
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.FcmCallData
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection

class CallViewModel(
    sessionManager: SessionManager
) : ViewModel() {

    companion object {
        private const val TAG = "CallViewModel"
        const val RINGING_TIMEOUT_MS = 15_000L
    }

    private var signalingManager: SignalingService? = null
    private var webRTCDataSource: WebRTCDataSource? = null
    private var callNotificationDataSource: CallNotificationDataSource? = null
    private var fcmDataSource: FCMDataSource? = null
    private var presenceDataSource: PresenceDataSource? = null
    private var contactDataSource: ContactDataSource? = null
    private var callHistoryDataSource: CallHistoryDataSource? = null
    private var quickDialDataSource: QuickDialDataSource? = null
    private var appointmentDataSource: AppointmentDataSource? = null

    private val currentUserId = sessionManager.getUserId() ?: ""
    private val currentUserName = "User"

    sealed class CallState {
        data object Idle : CallState()
        data class Initiating(val targetUserId: String, val contactName: String, val nickname: String? = null, val avatarUrl: String? = null) : CallState()
        data class RingingOutgoing(val targetUserId: String, val contactName: String, val nickname: String? = null, val avatarUrl: String? = null) : CallState()
        data class RingingIncoming(val callerId: String, val callerName: String, val callerUsername: String, val nickname: String? = null, val avatarUrl: String? = null) : CallState()
        data object Connecting : CallState()
        data class Connected(val remoteUserId: String, val remoteUserName: String, val isIncoming: Boolean, val nickname: String? = null, val avatarUrl: String? = null) : CallState()
        data class Ending(val reason: String) : CallState()
        data class Ended(val reason: String, val duration: Long = 0) : CallState()
        data class Failed(val error: String) : CallState()
    }

    private val _callState: MutableLiveData<CallState> = MutableLiveData(CallState.Idle)
    val callState: LiveData<CallState> = _callState

    private var currentCallId: String? = null
    private var remoteUserId: String? = null
    private var remoteUserName: String? = null
    private var isIncomingCall = false
    private var callStartTime: Long = 0
    private var pendingIceCandidates = mutableListOf<IceCandidate>()
    private var remoteUserContactId: Int? = null
    private var pendingOfferSdp: String? = null
    private var isCleaningUp = false

    private val _isMicrophoneAvailable = MutableLiveData(false)
    val isMicrophoneAvailable: LiveData<Boolean> = _isMicrophoneAvailable

    private val _microphoneModeMessage = MutableLiveData<String>()
    val microphoneModeMessage: LiveData<String> = _microphoneModeMessage

    private var ringingTimeoutJob: Job? = null

    private var callTimerJob: Job? = null
    private val _callDuration = MutableLiveData(0L)
    val callDuration: LiveData<Long> = _callDuration

    private val _isMuted = MutableLiveData(false)
    val isMuted: LiveData<Boolean> = _isMuted

    private val _isSpeakerOn = MutableLiveData(false)
    val isSpeakerOn: LiveData<Boolean> = _isSpeakerOn

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // Appointment notification (Phase 5)
    data class AppointmentNotification(
        val appointmentId: Int,
        val action: String,
        val date: String,
        val startTime: String,
        val endTime: String,
        val personnelName: String,
        val status: String,
        val shortMessage: String
    )

    private val _appointmentNotification = MutableLiveData<AppointmentNotification?>()
    val appointmentNotification: LiveData<AppointmentNotification?> = _appointmentNotification

    /**
     * Clears a delivered appointment notification.
     *
     * Without this the value stays set forever, and because the foreground service
     * observes with observeForever, re-registering the observer (on service
     * restart) immediately re-delivers the last event and relaunches
     * AppointmentActivity. The service calls this once it has shown the screen.
     */
    fun clearAppointmentNotification() {
        _appointmentNotification.postValue(null)
    }

    

    fun initialize(
        presenceDataSource: PresenceDataSource,
        contactDataSource: ContactDataSource,
        callHistoryDataSource: CallHistoryDataSource,
        quickDialDataSource: QuickDialDataSource,
        webRTCDataSource: WebRTCDataSource,
        callNotificationDataSource: CallNotificationDataSource,
        signalingService: SignalingService,
        fcmDataSource: FCMDataSource? = null,
        appointmentDataSource: AppointmentDataSource? = null
    ) {
        this.appointmentDataSource = appointmentDataSource

        if (currentUserId.isEmpty()) {
            Log.e(TAG, "Cannot initialize CallViewModel - no user ID")
            _errorMessage.value = "User not logged in"
            return
        }

        Log.d(TAG, "Initializing CallViewModel for user: $currentUserId")

        try {
            this.webRTCDataSource = webRTCDataSource.apply {
                initialize()

                onIceCandidate = { candidate ->
                    handleLocalIceCandidate(candidate)
                }

                onConnectionStateChange = { state ->
                    handleConnectionStateChange(state)
                }

                onMicrophoneModeChanged = { mode, message ->
                    _isMicrophoneAvailable.postValue(mode == MicrophoneMode.TWO_WAY)
                    _microphoneModeMessage.postValue(message)
                }
            }

            this.callNotificationDataSource = callNotificationDataSource
            this.fcmDataSource = fcmDataSource
            signalingManager = signalingService
            this.presenceDataSource = presenceDataSource
            this.contactDataSource = contactDataSource
            this.callHistoryDataSource = callHistoryDataSource
            this.quickDialDataSource = quickDialDataSource

            viewModelScope.launch {
                signalingManager?.initialize()
                observeSignalingEvents()
            }

            Log.i(TAG, "CallViewModel initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CallViewModel", e)
            _errorMessage.value = "Failed to initialize calling: ${e.message}"
        }
    }

    private suspend fun observeSignalingEvents() {
        signalingManager?.events?.collect { event ->
            Log.d(TAG, "Received signaling event: $event")

            when (event) {
                    is SignalingEvent.IncomingCall -> handleIncomingCall(event)
                is SignalingEvent.CallAnswered -> handleCallAnswered(event)
                is SignalingEvent.NewIceCandidate -> handleRemoteIceCandidate(event)
                is SignalingEvent.CallRejected -> handleCallRejected(event)
                is SignalingEvent.CallEnded -> handleCallEnded(event)
                is SignalingEvent.AppointmentEvent -> handleAppointmentEvent(event)
                is SignalingEvent.Error -> handleSignalingError(event)
                is SignalingEvent.Connected -> Log.d(TAG, "Signaling connected")
                is SignalingEvent.Disconnected -> Log.d(TAG, "Signaling disconnected")
            }
        }
    }

    private var remoteNickname: String? = null
    private var remoteAvatarUrl: String? = null

    private fun handleAppointmentEvent(event: SignalingEvent.AppointmentEvent) {
        Log.i(TAG, "Received appointment event: ${event.appointmentId} ${event.action}")

        // The pushed event means the backend's copy has changed, so anything
        // cached here is stale. The appointments screen refetches on resume, but
        // dropping the cache keeps the two in step even if that changes.
        appointmentDataSource?.invalidateCache()

        val notification = AppointmentNotification(
            appointmentId = event.appointmentId,
            action = event.action,
            date = event.date,
            startTime = event.startTime,
            endTime = event.endTime,
            personnelName = event.personnelName,
            status = event.status,
            shortMessage = event.shortMessage
        )

        _appointmentNotification.postValue(notification)
    }

    fun initiateCall(targetUserId: String, contactName: String, contactId: Int? = null, nickname: String? = null, avatarUrl: String? = null) {
        val currentState = _callState.value
        if (currentState !is CallState.Idle && currentState !is CallState.Ended && currentState !is CallState.Failed) {
            Log.w(TAG, "Cannot initiate call - already in call state: $currentState")
            _errorMessage.value = "Already in a call"
            return
        }

        if (currentState is CallState.Ended || currentState is CallState.Failed) {
            Log.d(TAG, "Resetting from $currentState to initiate new call")
            _callState.value = CallState.Idle
        }

        Log.i(TAG, "Initiating call to: $contactName (User ID: $targetUserId)")

        _errorMessage.value = null
        _callState.value = CallState.Initiating(targetUserId, contactName, nickname, avatarUrl)
        remoteUserId = targetUserId
        remoteUserName = contactName
        remoteUserContactId = contactId
        remoteNickname = nickname
        remoteAvatarUrl = avatarUrl
        isIncomingCall = false
        currentCallId = generateCallId()

        viewModelScope.launch {
            try {
                presenceDataSource?.updateWebRTCStatus("in_call")

                val offer = webRTCDataSource?.createOffer()

                if (offer != null) {
                    signalingManager?.sendCallOffer(
                        targetUserId = targetUserId,
                        callerName = currentUserName,
                        callerUsername = currentUserName,
                        offer = offer
                    )

                    fcmDataSource?.sendCallNotification(
                        targetUserId = targetUserId,
                        callerName = currentUserName,
                        callerUsername = currentUserName,
                        callerContactId = contactId?.toString() ?: "",
                        offerSdp = offer
                    )

                    _callState.value = CallState.RingingOutgoing(targetUserId, contactName, nickname, avatarUrl)
                    startRingingTimeout()
                    Log.i(TAG, "Call offer sent to $contactName")

                } else {
                    throw Exception("Failed to create WebRTC offer")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to initiate call", e)
                _callState.value = CallState.Failed("Failed to initiate call: ${e.message}")
                cleanup()
            }
        }
    }

    fun injectFcmIncomingCall(data: FcmCallData) {
        val currentState = _callState.value
        if (currentState !is CallState.Idle && currentState !is CallState.Ended && currentState !is CallState.Failed) {
            Log.w(TAG, "Cannot inject FCM call - already in call state: $currentState")
            return
        }

        Log.i(TAG, "Injecting FCM incoming call from: ${data.callerName}")

        remoteUserId = data.callerUserId
        remoteUserName = data.callerName
        remoteUserContactId = data.callerContactId.toIntOrNull()
        isIncomingCall = true
        currentCallId = generateCallId()
        pendingOfferSdp = data.offerSdp

        _callState.value = CallState.RingingIncoming(
            callerId = data.callerContactId.ifEmpty { data.callerUserId },
            callerName = data.callerName,
            callerUsername = data.callerUsername
        )
    }

    fun answerCall() {
        val currentState = _callState.value
        if (currentState !is CallState.RingingIncoming) {
            Log.w(TAG, "Cannot answer - not in ringing incoming state")
            return
        }

        Log.i(TAG, "Answering call from: ${currentState.callerName}")

        _callState.value = CallState.Connecting

        viewModelScope.launch {
            try {
                presenceDataSource?.updateWebRTCStatus("in_call")

                val offerSdp = pendingOfferSdp ?: throw Exception("No offer SDP available")

                val answer = webRTCDataSource?.createAnswer(offerSdp)

                if (answer != null) {
                    signalingManager?.sendCallAnswer(
                        callerId = remoteUserId ?: currentState.callerId,
                        calleeName = currentUserName,
                        answer = answer
                    )

                    pendingIceCandidates.forEach { candidate ->
                        webRTCDataSource?.addIceCandidate(
                            candidateSdp = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex
                        )
                    }
                    pendingIceCandidates.clear()

                    _callState.value = CallState.Connected(
                        remoteUserId = currentState.callerId,
                        remoteUserName = currentState.callerName,
                        isIncoming = true,
                        nickname = remoteNickname,
                        avatarUrl = remoteAvatarUrl
                    )

                    startCallTimer()

                    callNotificationDataSource?.startService(currentState.callerName, isIncoming = true)

                    Log.i(TAG, "Call answered successfully")

                } else {
                    throw Exception("Failed to create WebRTC answer")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to answer call", e)
                _callState.value = CallState.Failed("Failed to answer call: ${e.message}")
                cleanup()
            }
        }
    }

    fun rejectCall() {
        val currentState = _callState.value
        if (currentState !is CallState.RingingIncoming) {
            Log.w(TAG, "Cannot reject - not in ringing incoming state")
            return
        }

        Log.i(TAG, "Rejecting call from: ${currentState.callerName}")

        viewModelScope.launch {
            try {
                signalingManager?.rejectCall(
                    callerId = remoteUserId ?: currentState.callerId,
                    reason = "user_declined"
                )

                _callState.value = CallState.Ended("rejected", 0)

                logCallToHistory("missed", 0, "user_declined")

                cleanup()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to reject call", e)
                cleanup()
            }
        }
    }

    fun endCall() {
        val currentState = _callState.value

        Log.i(TAG, "Ending call - current state: $currentState")

        _callState.value = CallState.Ending("user_hangup")

        viewModelScope.launch {
            try {
                val duration = if (callStartTime > 0) {
                    (System.currentTimeMillis() - callStartTime) / 1000
                } else {
                    0L
                }

                remoteUserId?.let { remoteId ->
                    signalingManager?.endCall(
                        otherUserId = remoteId,
                        reason = "user_hangup",
                        duration = duration
                    )
                }

                _callState.value = CallState.Ended("user_hangup", duration)

                val callType = if (isIncomingCall) "incoming" else "outgoing"
                logCallToHistory(callType, duration, "user_hangup")

                cleanup()

            } catch (e: Exception) {
                Log.e(TAG, "Failed to end call gracefully", e)
                cleanup()
            }
        }
    }

    fun getRemoteUserId(): String? = remoteUserId
    fun getRemoteUserName(): String? = remoteUserName
    fun isIncomingCall(): Boolean = isIncomingCall

    fun toggleMute() {
        val currentlyMuted = _isMuted.value ?: false
        if (currentlyMuted) {
            webRTCDataSource?.unmute()
            _isMuted.value = false
            Log.d(TAG, "Microphone unmuted")
        } else {
            webRTCDataSource?.mute()
            _isMuted.value = true
            Log.d(TAG, "Microphone muted")
        }
    }

    fun toggleSpeaker() {
        val currentlySpeaker = _isSpeakerOn.value ?: false
        if (currentlySpeaker) {
            webRTCDataSource?.disableSpeaker()
            _isSpeakerOn.value = false
            Log.d(TAG, "Speaker disabled")
        } else {
            webRTCDataSource?.enableSpeaker()
            _isSpeakerOn.value = true
            Log.d(TAG, "Speaker enabled")
        }
    }

    private fun handleIncomingCall(event: SignalingEvent.IncomingCall) {
        Log.i(TAG, "Incoming call from: ${event.callerName} (${event.callerId})")

        val currentState = _callState.value
        if (currentState !is CallState.Idle && currentState !is CallState.Ended && currentState !is CallState.Failed) {
            Log.w(TAG, "Rejecting incoming call - already in call state: $currentState")
            viewModelScope.launch {
                signalingManager?.rejectCall(event.callerUserId, "busy")
            }
            return
        }

        viewModelScope.launch {
            var callerContactId = event.callerId.toIntOrNull()
            var contact: Contact? = null

            if (callerContactId != null) {
                contact = contactDataSource?.getContactByContactId(callerContactId)
            } else {
                Log.d(TAG, "callerId is UUID, looking up profile for: ${event.callerUserId}")
                try {
                    val result = presenceDataSource?.getUserPresence(event.callerUserId)
                    val callerProfile = result?.getOrNull()
                    if (callerProfile != null) {
                        val resolvedId = callerProfile.contactId
                        callerContactId = resolvedId
                        contact = contactDataSource?.getContactByContactId(resolvedId)
                        Log.d(TAG, "Resolved UUID to contact_id: $callerContactId")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to resolve caller UUID to contact_id", e)
                }
            }

            val displayName = contact?.username ?: event.callerName.takeIf { it != "Unknown Caller" } ?: "Unknown Caller"
            val callerNickname = contact?.nickname
            val callerAvatarUrl = contact?.avatarUrl

            Log.d(TAG, "Caller lookup: ${if (contact != null) "Found '${contact.username}' (nickname='${callerNickname}') in contacts" else "Not in contacts, showing '$displayName'"}")

            remoteUserId = event.callerUserId
            remoteUserName = displayName
            remoteUserContactId = callerContactId
            remoteNickname = callerNickname
            remoteAvatarUrl = callerAvatarUrl
            isIncomingCall = true
            currentCallId = generateCallId()
            pendingOfferSdp = event.offer

            _callState.value = CallState.RingingIncoming(
                callerContactId?.toString() ?: event.callerId,
                displayName,
                event.callerUsername,
                callerNickname,
                callerAvatarUrl
            )
        }
    }

    private fun handleCallAnswered(event: SignalingEvent.CallAnswered) {
        Log.i(TAG, "Call answered by remote peer")

        val currentState = _callState.value
        if (currentState !is CallState.RingingOutgoing && currentState !is CallState.Initiating) {
            Log.w(TAG, "Ignoring call answer - not in outgoing ringing state (current: $currentState)")
            return
        }

        cancelRingingTimeout()

        viewModelScope.launch {
            try {
                val result = webRTCDataSource?.handleAnswer(event.answer)

                if (result?.isSuccess == true) {
                    pendingIceCandidates.forEach { candidate ->
                        webRTCDataSource?.addIceCandidate(
                            candidateSdp = candidate.sdp,
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex
                        )
                    }
                    pendingIceCandidates.clear()

                    _callState.value = CallState.Connected(
                        remoteUserId = remoteUserId ?: "",
                        remoteUserName = remoteUserName ?: "Unknown",
                        isIncoming = false,
                        nickname = remoteNickname,
                        avatarUrl = remoteAvatarUrl
                    )

                    startCallTimer()

                    callNotificationDataSource?.startService(remoteUserName ?: "Unknown", isIncoming = false)
                } else {
                    throw Exception("Failed to set remote answer")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle call answered", e)
                _callState.value = CallState.Failed("Failed to connect: ${e.message}")
                cleanup()
            }
        }
    }

    private fun handleRemoteIceCandidate(event: SignalingEvent.NewIceCandidate) {
        Log.d(TAG, "Received remote ICE candidate")

        val candidate = IceCandidate(event.sdpMid, event.sdpMLineIndex ?: 0, event.candidate)

        if (_callState.value is CallState.Connected) {
            webRTCDataSource?.addIceCandidate(
                candidateSdp = candidate.sdp,
                sdpMid = candidate.sdpMid,
                sdpMLineIndex = candidate.sdpMLineIndex
            )
        } else {
            Log.d(TAG, "Queueing ICE candidate until call is connected")
            pendingIceCandidates.add(candidate)
        }
    }

    private fun handleLocalIceCandidate(candidate: IceCandidate) {
        Log.d(TAG, "Generated local ICE candidate")

        viewModelScope.launch {
            remoteUserId?.let { remoteId ->
                signalingManager?.sendIceCandidate(
                    targetUserId = remoteId,
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            }
        }
    }

    private fun handleConnectionStateChange(state: PeerConnection.IceConnectionState) {
        Log.d(TAG, "ICE connection state changed: $state")

        when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                Log.i(TAG, "WebRTC connection established")
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                Log.w(TAG, "WebRTC connection disconnected")
            }
            PeerConnection.IceConnectionState.FAILED -> {
                Log.e(TAG, "WebRTC connection failed")
                if (isCleaningUp) {
                    Log.d(TAG, "Ignoring FAILED state during cleanup")
                    return
                }
                _callState.postValue(CallState.Failed("Connection failed"))
                viewModelScope.launch {
                    cleanup()
                }
            }
            PeerConnection.IceConnectionState.CLOSED -> {
                Log.i(TAG, "WebRTC connection closed")
                if (isCleaningUp) {
                    Log.d(TAG, "Ignoring CLOSED state during cleanup")
                }
            }
            else -> {}
        }
    }

    private suspend fun handleCallRejected(event: SignalingEvent.CallRejected) {
        Log.i(TAG, "Call rejected: ${event.reason}")
        _callState.value = CallState.Ended("rejected", 0)
        logCallToHistory("outgoing", 0, "rejected")
        cleanup()
    }

    private suspend fun handleCallEnded(event: SignalingEvent.CallEnded) {
        Log.i(TAG, "Call ended: ${event.reason}")
        _callState.value = CallState.Ended(event.reason, event.duration)
        val callType = if (isIncomingCall) "incoming" else "outgoing"
        logCallToHistory(callType, event.duration, event.reason)
        cleanup()
    }

    private fun handleSignalingError(event: SignalingEvent.Error) {
        Log.e(TAG, "Signaling error: ${event.message}", event.exception)
        _errorMessage.value = event.message
    }

    private fun startRingingTimeout() {
        cancelRingingTimeout()
        ringingTimeoutJob = viewModelScope.launch {
            delay(RINGING_TIMEOUT_MS)
            val currentState = _callState.value
            if (currentState is CallState.RingingOutgoing) {
                Log.i(TAG, "Ringing timeout - no answer after ${RINGING_TIMEOUT_MS / 1000}s")
                _callState.value = CallState.Ended("no_answer", 0)
                logCallToHistory("outgoing", 0, "no_answer")
                remoteUserId?.let { remoteId ->
                    signalingManager?.endCall(
                        otherUserId = remoteId,
                        reason = "no_answer",
                        duration = 0
                    )
                }
                cleanup()
            }
        }
    }

    private fun cancelRingingTimeout() {
        ringingTimeoutJob?.cancel()
        ringingTimeoutJob = null
    }

    private fun startCallTimer() {
        callStartTime = System.currentTimeMillis()
        _callDuration.value = 0L

        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val duration = (System.currentTimeMillis() - callStartTime) / 1000
                _callDuration.postValue(duration)

                callNotificationDataSource?.updateCallDuration(
                    durationSeconds = duration,
                    isMuted = _isMuted.value ?: false,
                    isSpeakerOn = _isSpeakerOn.value ?: false
                )
            }
        }
    }

    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null

        callNotificationDataSource?.stopService()
    }

    private fun generateCallId(): String {
        return "call_${currentUserId}_${System.currentTimeMillis()}"
    }

    private suspend fun logCallToHistory(callType: String, duration: Long, endReason: String) {
        try {
            val entry = CallHistoryInsert(
                userId = currentUserId,
                contactId = remoteUserContactId,
                contactName = remoteUserName,
                callType = callType,
                callDuration = duration.toInt(),
                endReason = endReason
            )
            callHistoryDataSource?.logCall(entry)
            quickDialDataSource?.invalidateCache()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log call to history", e)
        }
    }

    private suspend fun cleanup() {
        if (isCleaningUp) {
            Log.d(TAG, "Cleanup already in progress - skipping")
            return
        }
        isCleaningUp = true
        Log.d(TAG, "Cleaning up call resources")

        cancelRingingTimeout()
        stopCallTimer()

        remoteUserId?.let { remoteId ->
            try {
                Log.d(TAG, "Notifying remote user $remoteId of cleanup")
                signalingManager?.endCall(otherUserId = remoteId, reason = "cleanup", duration = 0)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify remote of cleanup", e)
            }
        }

        webRTCDataSource?.close()

        presenceDataSource?.updateWebRTCStatus("available")

        remoteUserId = null
        remoteUserName = null
        remoteUserContactId = null
        currentCallId = null
        isIncomingCall = false
        callStartTime = 0
        pendingIceCandidates.clear()
        pendingOfferSdp = null
        _isMuted.value = false
        _isSpeakerOn.value = false
        _callDuration.value = 0L
        _errorMessage.value = null

        isCleaningUp = false

        viewModelScope.launch {
            delay(500)
            if (_callState.value is CallState.Ended || _callState.value is CallState.Failed) {
                _callState.value = CallState.Idle
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "CallViewModel cleared")

        stopCallTimer()
        webRTCDataSource?.cleanup()
        viewModelScope.launch {
            signalingManager?.cleanup()
        }
    }
}
