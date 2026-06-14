package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.datasource.ProfileDataSource
import com.example.tv_caller_app.datasource.UsernameNotUniqueException
import com.example.tv_caller_app.model.Profile
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ProfileRepository private constructor(
    private val sessionManager: SessionManager
) : ProfileDataSource {

    private val supabase = SupabaseClient.client

    companion object {
        private const val TAG = "ProfileRepository"

        @Volatile
        private var instance: ProfileRepository? = null

        fun getInstance(sessionManager: SessionManager): ProfileRepository {
            return instance ?: synchronized(this) {
                instance ?: ProfileRepository(sessionManager).also { instance = it }
            }
        }
    }

    private fun getUserId(): String {
        return sessionManager.getUserId()
            ?: throw IllegalStateException("User not logged in")
    }

    private suspend fun ensureSession() {
        if (supabase.auth.currentSessionOrNull() != null) return
        val accessToken = sessionManager.getAccessToken() ?: return
        val refreshToken = sessionManager.getRefreshToken() ?: return
        try {
            supabase.auth.importSession(
                UserSession(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    tokenType = "Bearer",
                    expiresIn = 3600,
                    user = null
                )
            )
            supabase.auth.refreshCurrentSession()
            Log.d(TAG, "Session restored before upload")
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore session: ${e.message}")
        }
    }

    override suspend fun getProfile(): Profile? {
        return try {
            val userId = getUserId()
            Log.d(TAG, "Fetching profile for user: ${userId.take(8)}...")

            val profiles = supabase.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeList<Profile>()

            profiles.firstOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching profile", e)
            null
        }
    }

    override suspend fun isUsernameTaken(username: String): Boolean {
        return try {
            val userId = getUserId()

            @Serializable
            data class IdRow(val id: String)

            val results = supabase.from("profiles")
                .select(columns = Columns.list("id")) {
                    filter {
                        eq("username", username)
                        neq("id", userId)
                    }
                }
                .decodeList<IdRow>()

            results.isNotEmpty()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking username availability", e)
            false
        }
    }

    override suspend fun updateUsername(username: String): Boolean {
        return try {
            val userId = getUserId()
            Log.d(TAG, "Updating username to '$username'")

            val update = buildJsonObject {
                put("username", username)
            }

            supabase.from("profiles")
                .update(update) {
                    filter {
                        eq("id", userId)
                    }
                }

            sessionManager.saveUsername(username)
            Log.d(TAG, "Username updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating username", e)
            if (e.message?.contains("unique", ignoreCase = true) == true ||
                e.message?.contains("duplicate", ignoreCase = true) == true ||
                e.message?.contains("profiles_username_key", ignoreCase = true) == true) {
                throw UsernameNotUniqueException()
            }
            false
        }
    }

    override suspend fun uploadAvatar(imageBytes: ByteArray): String? {
        ensureSession()
        val userId = getUserId()
        val path = "$userId/avatar.jpg"
        Log.d(TAG, "Uploading avatar to: $path (${imageBytes.size} bytes)")

        val bucket = supabase.storage.from("avatars")
        bucket.upload(path, imageBytes, upsert = true)

        val publicUrl = bucket.publicUrl(path)
        Log.d(TAG, "Avatar uploaded, public URL: $publicUrl")

        updateAvatarUrl(publicUrl)
        
        
        return "$publicUrl?v=${System.currentTimeMillis()}"
    }

    override suspend fun setPresetAvatar(avatarName: String): Boolean {
        return try {
            updateAvatarUrl("preset:$avatarName")
            Log.d(TAG, "Preset avatar set: $avatarName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting preset avatar", e)
            false
        }
    }

    private suspend fun updateAvatarUrl(url: String?): Boolean {
        return try {
            val userId = getUserId()

            val update = buildJsonObject {
                put("avatar_url", url)
            }

            supabase.from("profiles")
                .update(update) {
                    filter {
                        eq("id", userId)
                    }
                }

            Log.d(TAG, "Avatar URL updated")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating avatar URL", e)
            false
        }
    }

    override suspend fun deleteAvatar(): Boolean {
        return try {
            val userId = getUserId()
            val path = "$userId/avatar.jpg"

            try {
                val bucket = supabase.storage.from("avatars")
                bucket.delete(path)
            } catch (_: Exception) {
                Log.w(TAG, "No storage file to delete (might be preset avatar)")
            }

            updateAvatarUrl(null)

            Log.d(TAG, "Avatar deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting avatar", e)
            false
        }
    }
}
