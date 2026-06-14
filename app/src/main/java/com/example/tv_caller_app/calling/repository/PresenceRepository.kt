package com.example.tv_caller_app.calling.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.datasource.PresenceDataSource
import com.example.tv_caller_app.model.Profile
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class PresenceRepository private constructor(
    private val sessionManager: SessionManager
) : PresenceDataSource {
    private val supabase = SupabaseClient.client
    private val scope = CoroutineScope(Dispatchers.IO)

    private var heartbeatJob: Job? = null

    companion object {
        private const val TAG = "PresenceRepository"
        private const val HEARTBEAT_INTERVAL_MS = 30000L
        @Volatile
        private var instance: PresenceRepository? = null

        private val ALWAYS_ONLINE_CONTACT_IDS = setOf(
            503035273,
            955647202,
            218221166
        )

        fun getInstance(sessionManager: SessionManager): PresenceRepository {
            return instance ?: synchronized(this) {
                instance ?: PresenceRepository(sessionManager).also { instance = it }
            }
        }

        fun isAlwaysOnline(contactId: Int): Boolean {
            return contactId in ALWAYS_ONLINE_CONTACT_IDS
        }
    }

    override suspend fun setOnline(deviceType: String): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("User not logged in"))

            Log.i(TAG, "Setting user online: $userId")

            supabase.postgrest.rpc("set_user_online", buildJsonObject {
                put("user_uuid", userId)
                put("p_device_type", deviceType)
            })

            startHeartbeat()

            Log.i(TAG, "User set online successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user online", e)
            Result.failure(e)
        }
    }

    override suspend fun setOffline(): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("User not logged in"))

            Log.i(TAG, "Setting user offline: $userId")

            stopHeartbeat()

            supabase.postgrest.rpc("set_user_offline", buildJsonObject {
                put("user_uuid", userId)
            })

            Log.i(TAG, "User set offline successfully")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user offline", e)
            Result.failure(e)
        }
    }

    override suspend fun updateWebRTCStatus(status: String): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("User not logged in"))

            Log.d(TAG, "Updating WebRTC status: $status")

            val update = buildJsonObject {
                put("webrtc_status", status)
                put("last_seen", Instant.now().toString())
            }

            supabase.from("profiles")
                .update(update) {
                    filter {
                        eq("id", userId)
                    }
                }

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to update WebRTC status", e)
            Result.failure(e)
        }
    }

    override suspend fun getOnlineUsers(): Result<List<Profile>> {
        return try {
            val userId = sessionManager.getUserId()
                ?: return Result.failure(Exception("User not logged in"))

            Log.d(TAG, "Fetching online users for user: $userId")

            val onlineProfiles = supabase.from("profiles")
                .select(columns = Columns.list("id", "username", "email", "contact_id", "is_online", "last_seen", "device_type", "webrtc_status", "created_at", "updated_at")) {
                    filter {
                        eq("is_online", true)
                        neq("id", userId)
                    }
                }
                .decodeList<Profile>()

            val onlineContactIds = onlineProfiles.map { it.contactId }.toSet()
            val missingDummyIds = ALWAYS_ONLINE_CONTACT_IDS.filter { it !in onlineContactIds }

            val dummyProfiles = if (missingDummyIds.isNotEmpty()) {
                supabase.from("profiles")
                    .select(columns = Columns.list("id", "username", "email", "contact_id", "is_online", "last_seen", "device_type", "webrtc_status", "created_at", "updated_at")) {
                        filter {
                            isIn("contact_id", missingDummyIds)
                            neq("id", userId)
                        }
                    }
                    .decodeList<Profile>()
                    .map { it.copy(isOnline = true, webrtcStatus = "available") }
            } else {
                emptyList()
            }

            val allProfiles = onlineProfiles + dummyProfiles

            Log.d(TAG, "Found ${allProfiles.size} online users (${dummyProfiles.size} dummy)")
            allProfiles.forEach { profile ->
                Log.d(TAG, "  - ${profile.username} (contactId=${profile.contactId}, webrtc=${profile.webrtcStatus})")
            }

            Result.success(allProfiles)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch online users", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun getUserPresence(userId: String): Result<Profile> {
        return try {
            Log.d(TAG, "Fetching presence for user UUID: $userId")

            val profile = supabase.from("profiles")
                .select(columns = Columns.list("id", "username", "email", "contact_id", "is_online", "last_seen", "device_type", "webrtc_status", "created_at", "updated_at")) {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingle<Profile>()

            val effectiveProfile = if (isAlwaysOnline(profile.contactId)) {
                profile.copy(isOnline = true, webrtcStatus = "available")
            } else {
                profile
            }

            Log.d(TAG, "Found user: ${effectiveProfile.username}, isOnline=${effectiveProfile.isOnline}, webrtc=${effectiveProfile.webrtcStatus}")
            Result.success(effectiveProfile)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user presence by UUID", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun getUserPresenceByContactId(contactId: Int): Result<Profile> {
        return try {
            Log.d(TAG, "Fetching presence for contact ID: $contactId")

            val profile = supabase.from("profiles")
                .select(columns = Columns.list("id", "username", "email", "contact_id", "is_online", "last_seen", "device_type", "webrtc_status", "created_at", "updated_at")) {
                    filter {
                        eq("contact_id", contactId)
                    }
                }
                .decodeSingle<Profile>()

            val effectiveProfile = if (isAlwaysOnline(contactId)) {
                profile.copy(isOnline = true, webrtcStatus = "available")
            } else {
                profile
            }

            Log.d(TAG, "Found user: ${effectiveProfile.username}, isOnline=${effectiveProfile.isOnline}, webrtc=${effectiveProfile.webrtcStatus}")
            Result.success(effectiveProfile)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch user presence by contact ID", e)
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun startHeartbeat() {
        stopHeartbeat()

        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    delay(HEARTBEAT_INTERVAL_MS)

                    val userId = sessionManager.getUserId() ?: break

                    val update = buildJsonObject {
                        put("last_seen", Instant.now().toString())
                    }

                    supabase.from("profiles")
                        .update(update) {
                            filter {
                                eq("id", userId)
                            }
                        }

                    Log.d(TAG, "Heartbeat sent")

                } catch (e: Exception) {
                    Log.e(TAG, "Heartbeat failed", e)
                }
            }
        }

        Log.i(TAG, "Heartbeat started")
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        Log.i(TAG, "Heartbeat stopped")
    }

    override suspend fun cleanup() {
        setOffline()
        instance = null
    }
}
