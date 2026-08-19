package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.CareTeamMember
import com.example.tv_caller_app.model.PatientProfile
import com.example.tv_caller_app.network.BackendApiClient
import com.example.tv_caller_app.network.BackendGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The signed-in patient's identity and care team, from the .NET backend.
 *
 * Same shape as [AppointmentRepository] — a singleton over SessionManager with a
 * short in-memory cache. Cached more willingly than appointments are, because a
 * name and a care team change on the order of months, not hours.
 */
class PatientProfileRepository private constructor(
    private val sessionManager: SessionManager
) {

    private var cachedProfile: PatientProfile? = null
    private var profileTimestamp: Long = 0

    private var cachedCareTeam: List<CareTeamMember>? = null
    private var careTeamTimestamp: Long = 0

    companion object {
        private const val TAG = "PatientProfileRepository"
        private const val CACHE_DURATION_MS = 30 * 60 * 1000L
        private const val PATH_ME = "api/tv/me"
        private const val PATH_CARE_TEAM = "api/tv/careteam/mine"

        @Volatile
        private var instance: PatientProfileRepository? = null

        fun getInstance(sessionManager: SessionManager): PatientProfileRepository {
            return instance ?: synchronized(this) {
                instance ?: PatientProfileRepository(sessionManager).also { instance = it }
            }
        }
    }

    /**
     * The patient's own record. Throws the same exceptions as
     * [AppointmentRepository]; callers that only want a greeting should prefer
     * [fullNameOrNull], which degrades instead.
     */
    suspend fun getMyProfile(forceRefresh: Boolean = false): PatientProfile {
        if (!forceRefresh && isFresh(profileTimestamp)) {
            cachedProfile?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val profile: PatientProfile =
                BackendApiClient.json.decodeFromString(BackendGet.text(PATH_ME, sessionManager))

            cachedProfile = profile
            profileTimestamp = System.currentTimeMillis()
            profile
        }
    }

    /**
     * The patient's real name, or null if it cannot be fetched.
     *
     * A greeting must never take the screen down or block the header, so every
     * failure — unlinked profile, expired session, no backend configured — collapses
     * to null here and the caller falls back to the cached username.
     */
    suspend fun fullNameOrNull(): String? =
        runCatching { getMyProfile().fullName.takeIf { it.isNotBlank() } }
            .onFailure { Log.w(TAG, "Could not load the patient's name: ${it.message}") }
            .getOrNull()

    /** The patient's care team, empty if none is linked. */
    suspend fun getMyCareTeam(forceRefresh: Boolean = false): List<CareTeamMember> {
        if (!forceRefresh && isFresh(careTeamTimestamp)) {
            cachedCareTeam?.let { return it }
        }

        return withContext(Dispatchers.IO) {
            val team: List<CareTeamMember> =
                BackendApiClient.json.decodeFromString(
                    BackendGet.text(PATH_CARE_TEAM, sessionManager)
                )

            cachedCareTeam = team
            careTeamTimestamp = System.currentTimeMillis()
            team
        }
    }

    /** Clears both caches. Called on logout — this is clinical data on a shared device. */
    fun invalidateCache() {
        cachedProfile = null
        profileTimestamp = 0
        cachedCareTeam = null
        careTeamTimestamp = 0
        Log.d(TAG, "Cache invalidated")
    }

    private fun isFresh(timestamp: Long): Boolean =
        System.currentTimeMillis() - timestamp < CACHE_DURATION_MS
}
