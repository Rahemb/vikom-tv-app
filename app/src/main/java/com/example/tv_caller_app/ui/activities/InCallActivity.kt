package com.example.tv_caller_app.ui.activities

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import java.util.Locale
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

import com.example.tv_caller_app.R
import com.example.tv_caller_app.TVCallerApplication
import com.example.tv_caller_app.calling.audio.MicrophoneStatusHandler
import com.example.tv_caller_app.ui.util.AvatarHelper
import com.example.tv_caller_app.viewmodel.CallViewModel

class InCallActivity : FragmentActivity() {

    private lateinit var txtContactName: TextView
    private lateinit var txtCallStatus: TextView
    private lateinit var txtCallTimer: TextView
    private lateinit var txtMuteStatus: TextView
    private lateinit var txtSpeakerStatus: TextView
    private lateinit var btnMute: ImageButton
    private lateinit var btnSpeaker: ImageButton
    private lateinit var btnHangUp: ImageButton

    private lateinit var callViewModel: CallViewModel
    private lateinit var micHandler: MicrophoneStatusHandler

    private var contactId: String? = null
    private var contactName: String? = null
    private var contactNickname: String? = null
    private var contactAvatarUrl: String? = null
    private var callId: String? = null
    private var isIncoming: Boolean = false

    private lateinit var audioManager: AudioManager

    companion object {
        private const val TAG = "InCallActivity"
        const val EXTRA_CONTACT_ID = "contact_id"
        const val EXTRA_CONTACT_NAME = "contact_name"
        const val EXTRA_CONTACT_NICKNAME = "contact_nickname"
        const val EXTRA_CONTACT_AVATAR_URL = "contact_avatar_url"
        const val EXTRA_CALL_ID = "call_id"
        const val EXTRA_IS_INCOMING = "is_incoming"

        fun createIntent(
            context: Context,
            contactId: String,
            contactName: String,
            callId: String,
            isIncoming: Boolean = false,
            nickname: String? = null,
            avatarUrl: String? = null
        ): Intent {
            return Intent(context, InCallActivity::class.java).apply {
                putExtra(EXTRA_CONTACT_ID, contactId)
                putExtra(EXTRA_CONTACT_NAME, contactName)
                putExtra(EXTRA_CONTACT_NICKNAME, nickname)
                putExtra(EXTRA_CONTACT_AVATAR_URL, avatarUrl)
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_IS_INCOMING, isIncoming)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                callViewModel.endCall()
            }
        })

        setupWindow()

        setContentView(R.layout.activity_in_call)

        val app = application as TVCallerApplication
        val globalCallViewModel = app.getCallViewModel()

        if (globalCallViewModel == null) {
            Log.e(TAG, "Global CallViewModel not initialized!")
            Toast.makeText(this, getString(R.string.call_service_unavailable), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        callViewModel = globalCallViewModel
        micHandler = MicrophoneStatusHandler(this)

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_VOICE_CALL

        extractIntentData()
        initializeViews()
        updateUI()
        setupButtonListeners()
        observeViewModel()
        stopAnyVibration()
        configureAudioMode()

        micHandler.checkAndRequestPermission(
            onGranted = { Log.d(TAG, "Microphone permission granted") },
            onDenied  = { Log.w(TAG, "Microphone permission denied — receive-only mode") }
        )

        Log.d(TAG, "InCallActivity created - call with: $contactName")
    }

    private fun setupWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun extractIntentData() {
        contactId = intent.getStringExtra(EXTRA_CONTACT_ID)
        contactName = intent.getStringExtra(EXTRA_CONTACT_NAME)
        contactNickname = intent.getStringExtra(EXTRA_CONTACT_NICKNAME)
        contactAvatarUrl = intent.getStringExtra(EXTRA_CONTACT_AVATAR_URL)
        callId = intent.getStringExtra(EXTRA_CALL_ID)
        isIncoming = intent.getBooleanExtra(EXTRA_IS_INCOMING, false)

        Log.d(TAG, "Call with: $contactName (ID: $contactId, Incoming: $isIncoming)")
    }

    private fun initializeViews() {
        txtContactName = findViewById(R.id.txt_contact_name)
        txtCallStatus = findViewById(R.id.txt_call_status)
        txtCallTimer = findViewById(R.id.txt_call_timer)

        val txtNickname = findViewById<TextView>(R.id.txt_contact_nickname)
        if (!contactNickname.isNullOrBlank()) {
            txtNickname.text = contactNickname
            txtNickname.visibility = android.view.View.VISIBLE
        }

        val imgAvatar = findViewById<ImageView>(R.id.img_contact_avatar)
        AvatarHelper.loadAvatar(imgAvatar, contactAvatarUrl, R.drawable.call_avatar_circle)

        txtMuteStatus = findViewById(R.id.txt_mute_status)
        txtSpeakerStatus = findViewById(R.id.txt_speaker_status)
        btnMute = findViewById(R.id.btn_mute)
        btnSpeaker = findViewById(R.id.btn_speaker)
        btnHangUp = findViewById(R.id.btn_hang_up)

        btnHangUp.requestFocus()
    }

    private fun updateUI() {
        txtContactName.text = contactName ?: getString(R.string.unknown_contact)
        txtCallStatus.text = getString(R.string.status_connected)
        txtCallTimer.text = getString(R.string.call_timer_zero)
    }

    private fun setupButtonListeners() {
        btnMute.setOnClickListener {
            Log.d(TAG, "Mute button clicked")
            callViewModel.toggleMute()
        }

        btnSpeaker.setOnClickListener {
            Log.d(TAG, "Speaker button clicked")
            callViewModel.toggleSpeaker()
        }

        btnHangUp.setOnClickListener {
            Log.d(TAG, "Hang up button clicked")
            callViewModel.endCall()
        }
    }

    private fun observeViewModel() {
        callViewModel.callState.observe(this) { state ->
            Log.d(TAG, "Call state changed: $state")

            when (state) {
                is CallViewModel.CallState.Ended -> {
                    Log.i(TAG, "Call ended: ${state.reason}")
                    txtCallStatus.text = getString(R.string.call_ended)

                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                }
                is CallViewModel.CallState.Failed -> {
                    Log.e(TAG, "Call failed: ${state.error}")
                    txtCallStatus.text = getString(R.string.call_failed)

                    val intent = Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                }
                else -> {
                    Log.d(TAG, "State in InCallActivity: $state")
                }
            }
        }

        callViewModel.callDuration.observe(this) { duration ->
            val minutes = duration / 60
            val seconds = duration % 60
            txtCallTimer.text = String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }

        callViewModel.isMuted.observe(this) { isMuted ->
            updateMuteButton(isMuted)
        }

        callViewModel.isSpeakerOn.observe(this) { isSpeakerOn ->
            updateSpeakerButton(isSpeakerOn)
        }

        callViewModel.errorMessage.observe(this) { error ->
            error?.let {
                Log.e(TAG, "Error: $it")
                txtCallStatus.text = getString(R.string.error_prefix, it)
            }
        }

        var lastMicMessage = ""
        callViewModel.microphoneModeMessage.observe(this) { message ->
            lastMicMessage = message
        }

        callViewModel.isMicrophoneAvailable.observe(this) { isMicAvailable ->
            Log.d(TAG, "Microphone available: $isMicAvailable")
            micHandler.handleModeChange(isMicAvailable, lastMicMessage)
            btnMute.isEnabled = isMicAvailable
            btnMute.alpha = if (isMicAvailable) 1.0f else 0.5f
        }
    }

    private fun stopAnyVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator.cancel()
            Log.d(TAG, "Stopped any lingering vibration")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop vibration", e)
        }
    }

    private fun configureAudioMode() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val earpiece = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                }
                if (earpiece != null) {
                    audioManager.setCommunicationDevice(earpiece)
                } else {
                    audioManager.clearCommunicationDevice()
                }
            }

            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false

            Log.d(TAG, "Audio mode set to MODE_IN_COMMUNICATION, speaker OFF")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to configure audio mode", e)
        }
    }

    private fun updateMuteButton(isMuted: Boolean) {
        if (isMuted) {
            btnMute.setImageResource(R.drawable.ic_mic_off)
            txtMuteStatus.visibility = android.view.View.VISIBLE
            txtMuteStatus.text = getString(R.string.muted)
        } else {
            btnMute.setImageResource(R.drawable.ic_mic)
            txtMuteStatus.visibility = android.view.View.GONE
        }
    }

    private fun updateSpeakerButton(isSpeakerOn: Boolean) {
        if (isSpeakerOn) {
            btnSpeaker.setImageResource(R.drawable.ic_speaker_on)
            txtSpeakerStatus.visibility = android.view.View.VISIBLE
            txtSpeakerStatus.text = getString(R.string.speaker_on)
            txtSpeakerStatus.setTextColor(ContextCompat.getColor(this, R.color.speaker_active))
            txtSpeakerStatus.textSize = 16f
        } else {
            btnSpeaker.setImageResource(R.drawable.ic_speaker)
            txtSpeakerStatus.visibility = android.view.View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            audioManager.mode = AudioManager.MODE_NORMAL
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup audio", e)
        }

        Log.d(TAG, "InCallActivity destroyed")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        micHandler.handlePermissionResult(
            requestCode = requestCode,
            permissions = permissions,
            grantResults = grantResults,
            onGranted = { Log.d(TAG, "Permission granted during call") },
            onDenied  = { Log.w(TAG, "Permission denied during call — staying in receive-only mode") }
        )
    }
}
