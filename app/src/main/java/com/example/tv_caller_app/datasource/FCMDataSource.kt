package com.example.tv_caller_app.datasource

interface FCMDataSource {
    suspend fun updateToken(token: String): Result<Unit>
    suspend fun sendCallNotification(
        targetUserId: String,
        callerName: String,
        callerUsername: String,
        callerContactId: String,
        offerSdp: String
    ): Result<Unit>
}
