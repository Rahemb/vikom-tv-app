package com.example.tv_caller_app.calling.webrtc

import android.util.Log
import org.webrtc.*

class PeerConnectionObserver(
    private val onIceCandidateCallback: (IceCandidate) -> Unit,
    private val onConnectionChangeCallback: (PeerConnection.IceConnectionState) -> Unit,
    private val onAddStreamCallback: (MediaStream) -> Unit,
    private val onRemoveStreamCallback: (MediaStream) -> Unit
) : PeerConnection.Observer {

    companion object {
        private const val TAG = "PeerConnectionObserver"
    }

    override fun onIceCandidate(candidate: IceCandidate?) {
        candidate?.let {
            Log.d(TAG, "New ICE candidate: ${it.sdp}")
            onIceCandidateCallback(it)
        }
    }

    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        newState?.let {
            Log.i(TAG, "ICE connection state: $it")
            onConnectionChangeCallback(it)
        }
    }

    override fun onAddStream(stream: MediaStream?) {
        stream?.let {
            Log.i(TAG, "Remote stream added: ${it.id}")
            onAddStreamCallback(it)
        }
    }

    override fun onRemoveStream(stream: MediaStream?) {
        stream?.let {
            Log.i(TAG, "Remote stream removed: ${it.id}")
            onRemoveStreamCallback(it)
        }
    }

    override fun onDataChannel(dataChannel: DataChannel?) {
        Log.d(TAG, "Data channel: ${dataChannel?.label()}")
    }

    override fun onIceConnectionReceivingChange(receiving: Boolean) {
        Log.d(TAG, "ICE connection receiving: $receiving")
    }

    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
        Log.d(TAG, "ICE gathering state: $newState")
    }

    override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
        Log.d(TAG, "Signaling state: $newState")
    }

    override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
        Log.d(TAG, "ICE candidates removed: ${candidates?.size}")
    }

    override fun onRenegotiationNeeded() {
        Log.d(TAG, "Renegotiation needed")
    }

    override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
        Log.d(TAG, "Track added: ${receiver?.track()?.kind()}")
    }
}
