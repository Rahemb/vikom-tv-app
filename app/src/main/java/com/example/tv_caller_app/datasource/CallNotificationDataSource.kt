package com.example.tv_caller_app.datasource

interface CallNotificationDataSource {
    fun startService(contactName: String, isIncoming: Boolean)
    fun stopService()
    fun updateCallDuration(durationSeconds: Long, isMuted: Boolean, isSpeakerOn: Boolean)
}
