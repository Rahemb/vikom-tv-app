package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.datasource.FCMDataSource
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class FCMRepository private constructor(
    private val sessionManager: SessionManager
) : FCMDataSource {

    private val supabase = SupabaseClient.client

    companion object {
        private const val TAG = "FCMRepository"

        @Volatile
        private var instance: FCMRepository? = null

        fun getInstance(sessionManager: SessionManager): FCMRepository {
            return instance ?: synchronized(this) {
                instance ?: FCMRepository(sessionManager).also { instance = it }
            }
        }
    }

    override suspend fun updateToken(token: String): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(IllegalStateException("Not logged in"))
            supabase.from("profiles").update({
                set("fcm_token", token)
            }) {
                filter { eq("id", userId) }
            }
            Log.d(TAG, "FCM token updated in Supabase")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update FCM token", e)
            Result.failure(e)
        }
    }

    override suspend fun sendCallNotification(
        targetUserId: String,
        callerName: String,
        callerUsername: String,
        callerContactId: String,
        offerSdp: String
    ): Result<Unit> {
        return try {
            val body = buildJsonObject {
                put("target_user_id", targetUserId)
                put("caller_name", callerName)
                put("caller_username", callerUsername)
                put("caller_contact_id", callerContactId)
                put("offer_sdp", offerSdp)
            }
            supabase.functions.invoke("send-call-notification", body = body)
            Log.d(TAG, "FCM call notification sent to: $targetUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send FCM call notification", e)
            Result.failure(e)
        }
    }
}
