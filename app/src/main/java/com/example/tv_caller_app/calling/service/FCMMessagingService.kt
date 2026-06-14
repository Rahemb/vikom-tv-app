package com.example.tv_caller_app.calling.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.tv_caller_app.R
import com.example.tv_caller_app.TVCallerApplication
import com.example.tv_caller_app.model.FcmCallData
import com.example.tv_caller_app.ui.activities.IncomingCallActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FCMMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMMessagingService"
        private const val CHANNEL_ID = "incoming_call_channel"
        private const val NOTIFICATION_ID = 2001
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        val app = application as TVCallerApplication
        CoroutineScope(Dispatchers.IO).launch {
            app.updateFcmToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "FCM message received from: ${message.from}")

        val data = message.data
        val type = data["type"] ?: return

        if (type == "incoming_call") {
            val callerUserId = data["caller_user_id"] ?: return
            val callerName = data["caller_name"] ?: "Unknown"
            val callerUsername = data["caller_username"] ?: ""
            val callerContactId = data["caller_contact_id"] ?: ""
            val offerSdp = data["offer_sdp"] ?: return

            Log.i(TAG, "Incoming call from: $callerName")

            val callData = FcmCallData(
                callerUserId = callerUserId,
                callerName = callerName,
                callerUsername = callerUsername,
                callerContactId = callerContactId,
                offerSdp = offerSdp
            )

            val app = application as TVCallerApplication
            app.pendingFcmCallData = callData

            showIncomingCallNotification(callerName)
        }
    }

    private fun showIncomingCallNotification(callerName: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Incoming Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming calls"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_call)
            .setContentTitle(getString(R.string.incoming_call))
            .setContentText(callerName)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(pendingIntent, true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
