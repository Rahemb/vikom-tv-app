package com.example.tv_caller_app.calling.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Observer
import com.example.tv_caller_app.R
import com.example.tv_caller_app.TVCallerApplication
import com.example.tv_caller_app.calling.permissions.PermissionHelper
import com.example.tv_caller_app.ui.activities.IncomingCallActivity
import com.example.tv_caller_app.ui.activities.MainActivity
import com.example.tv_caller_app.ui.activities.AppointmentActivity
import com.example.tv_caller_app.viewmodel.CallViewModel

class SignalingForegroundService : Service() {

    companion object {
        private const val TAG = "SignalingFgService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "signaling_service_channel"
        private const val CHANNEL_NAME = "Call Listening"
        private const val INCOMING_CALL_CHANNEL_ID = "incoming_call_channel"
        private const val INCOMING_CALL_CHANNEL_NAME = "Incoming Calls"
        private const val INCOMING_CALL_NOTIFICATION_ID = 1003

        private const val ACTION_START = "com.example.tv_caller_app.ACTION_START_SIGNALING"
        private const val ACTION_STOP = "com.example.tv_caller_app.ACTION_STOP_SIGNALING"

        fun startService(context: Context) {
            val intent = Intent(context, SignalingForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            Log.d(TAG, "Service start requested")
        }

        fun stopService(context: Context) {
            val intent = Intent(context, SignalingForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            Log.d(TAG, "Service stop requested")
        }
    }

    private var callStateObserver: Observer<CallViewModel.CallState>? = null
    private var appointmentObserver: Observer<CallViewModel.AppointmentNotification?>? = null
    private var callViewModel: CallViewModel? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "SignalingForegroundService created")
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Starting signaling foreground service")
                if (!PermissionHelper.hasNotificationPermission(this)) {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted — persistent notification may not show on Android 13+")
                }
                startForeground(NOTIFICATION_ID, buildPersistentNotification())
                observeCallState()
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping signaling foreground service")
                removeCallStateObserver()
                dismissIncomingCallNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeCallStateObserver()
        Log.d(TAG, "SignalingForegroundService destroyed")
    }

    private fun observeCallState() {
        val app = application as? TVCallerApplication ?: return
        val vm = app.getCallViewModel() ?: return
        callViewModel = vm

        removeCallStateObserver()

        callStateObserver = Observer { state ->
            Log.d(TAG, "Call state in service: $state")
            when (state) {
                is CallViewModel.CallState.RingingIncoming -> {
                    Log.i(TAG, "Incoming call detected in service - launching IncomingCallActivity")
                    launchIncomingCallActivity(state)
                    showIncomingCallNotification(state)
                }
                is CallViewModel.CallState.Connecting,
                is CallViewModel.CallState.Connected,
                is CallViewModel.CallState.Ended,
                is CallViewModel.CallState.Idle -> {
                    dismissIncomingCallNotification()
                }
                else -> {}
            }
        }

        vm.callState.observeForever(callStateObserver!!)

        // Observe appointment notifications and launch a simple activity when received
        appointmentObserver = Observer { appointment ->
            appointment?.let { appt ->
                Log.i(TAG, "Appointment event received in service - launching AppointmentActivity: ${appt.appointmentId}")
                val intent = AppointmentActivity.createIntent(
                    context = this,
                    appointmentId = appt.appointmentId,
                    action = appt.action,
                    date = appt.date,
                    startTime = appt.startTime,
                    endTime = appt.endTime,
                    personnelName = appt.personnelName,
                    status = appt.status,
                    shortMessage = appt.shortMessage
                )
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        }

        vm.appointmentNotification.observeForever(appointmentObserver!!)
    }

    private fun removeCallStateObserver() {
        callStateObserver?.let { observer ->
            callViewModel?.callState?.removeObserver(observer)
        }
        appointmentObserver?.let { apptObserver ->
            callViewModel?.appointmentNotification?.removeObserver(apptObserver)
        }
        callStateObserver = null
        appointmentObserver = null
        callViewModel = null
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

            val signalingChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_ready_for_calls)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(signalingChannel)

            val incomingCallChannel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                INCOMING_CALL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.incoming_call)
                setShowBadge(true)
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(incomingCallChannel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, activityIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_ready_for_calls))
            .setSmallIcon(R.drawable.ic_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
    }

    private fun launchIncomingCallActivity(state: CallViewModel.CallState.RingingIncoming) {
        val intent = IncomingCallActivity.createIntent(
            context = this,
            callerId = state.callerId,
            callerName = state.callerName,
            callId = state.callerId + "_" + System.currentTimeMillis(),
            callerPhone = null,
            nickname = state.nickname,
            avatarUrl = state.avatarUrl
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Log.i(TAG, "IncomingCallActivity launched directly from service")
    }

    private fun showIncomingCallNotification(state: CallViewModel.CallState.RingingIncoming) {
        val fullScreenIntent = IncomingCallActivity.createIntent(
            context = this,
            callerId = state.callerId,
            callerName = state.callerName,
            callId = state.callerId + "_" + System.currentTimeMillis(),
            callerPhone = null,
            nickname = state.nickname,
            avatarUrl = state.avatarUrl
        )

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 1, fullScreenIntent, pendingIntentFlags
        )

        val notification = NotificationCompat.Builder(this, INCOMING_CALL_CHANNEL_ID)
            .setContentTitle(getString(R.string.incoming_call))
            .setContentText(getString(R.string.notification_incoming_call_from, state.callerName))
            .setSmallIcon(R.drawable.ic_call)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
    }

    private fun dismissIncomingCallNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID)
    }
}
