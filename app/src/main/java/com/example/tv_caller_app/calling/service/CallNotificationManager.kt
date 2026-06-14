package com.example.tv_caller_app.calling.service

import android.content.Context
import com.example.tv_caller_app.datasource.CallNotificationDataSource

class CallNotificationManager(private val context: Context) : CallNotificationDataSource {

    override fun startService(contactName: String, isIncoming: Boolean) {
        CallService.startService(context, contactName, isIncoming)
    }

    override fun stopService() {
        CallService.stopService(context)
    }

    override fun updateCallDuration(durationSeconds: Long, isMuted: Boolean, isSpeakerOn: Boolean) {
        CallService.updateCallDuration(context, durationSeconds, isMuted, isSpeakerOn)
    }
}
