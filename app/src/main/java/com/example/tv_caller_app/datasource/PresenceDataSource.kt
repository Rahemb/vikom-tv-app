package com.example.tv_caller_app.datasource

import com.example.tv_caller_app.model.Profile

interface PresenceDataSource {
    suspend fun setOnline(deviceType: String = "tv"): Result<Unit>
    suspend fun setOffline(): Result<Unit>
    suspend fun updateWebRTCStatus(status: String): Result<Unit>
    suspend fun getOnlineUsers(): Result<List<Profile>>
    suspend fun getUserPresence(userId: String): Result<Profile>
    suspend fun getUserPresenceByContactId(contactId: Int): Result<Profile>
    suspend fun cleanup()
}
