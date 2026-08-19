package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.datasource.AppointmentDataSource
import com.example.tv_caller_app.datasource.BackendNotConfiguredException
import com.example.tv_caller_app.datasource.ProfileNotLinkedException
import com.example.tv_caller_app.datasource.SessionExpiredException
import com.example.tv_caller_app.model.Appointment
import com.example.tv_caller_app.network.BackendApiClient
import com.example.tv_caller_app.network.BackendGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches the signed-in patient's appointments from the .NET backend, presenting
 * the Supabase access token as the bearer credential.
 *
 * Follows the same shape as [ContactRepository]: a singleton over SessionManager
 * with a short in-memory cache. The cache is intentionally a fallback rather than
 * the primary path — see [getMyAppointments].
 */
class AppointmentRepository private constructor(
    private val sessionManager: SessionManager
) : AppointmentDataSource {

    private var cachedAppointments: List<Appointment>? = null
    private var cacheTimestamp: Long = 0

    companion object {
        private const val TAG = "AppointmentRepository"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L
        private const val PATH = "api/tv/appointments/mine"

        @Volatile
        private var instance: AppointmentRepository? = null

        fun getInstance(sessionManager: SessionManager): AppointmentRepository {
            return instance ?: synchronized(this) {
                instance ?: AppointmentRepository(sessionManager).also { instance = it }
            }
        }
    }

    private fun isCacheValid(): Boolean {
        return cachedAppointments != null &&
               (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    override fun invalidateCache() {
        Log.d(TAG, "Cache invalidated")
        cachedAppointments = null
        cacheTimestamp = 0
    }

    /**
     * Callers normally pass forceRefresh = true (the appointments screen does so on
     * every resume). Supabase broadcasts are fire-and-forget with no replay, so a
     * cancellation that arrives while the tablet is asleep or offline is lost
     * forever — showing a stale medical appointment is worse than one extra
     * request. The cache therefore exists mainly to keep something on screen when
     * the network fails.
     */
    override suspend fun getMyAppointments(forceRefresh: Boolean): List<Appointment> {
        if (!forceRefresh && isCacheValid()) {
            Log.d(TAG, "Returning cached appointments (${cachedAppointments!!.size})")
            return cachedAppointments!!
        }

        return try {
            val appointments = withContext(Dispatchers.IO) { fetch() }
            cachedAppointments = appointments
            cacheTimestamp = System.currentTimeMillis()
            Log.d(TAG, "Fetched ${appointments.size} appointments")
            appointments
        } catch (e: ProfileNotLinkedException) {
            throw e
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: BackendNotConfiguredException) {
            throw e
        } catch (e: Exception) {
            // Transient failures fall back to whatever we last saw, matching how
            // the contact and quick-dial repositories behave.
            Log.e(TAG, "Error fetching appointments: ${e.message}", e)
            cachedAppointments?.let {
                Log.w(TAG, "Returning stale cached appointments (${it.size})")
                return it
            }
            throw e
        }
    }

    private suspend fun fetch(): List<Appointment> =
        BackendApiClient.json.decodeFromString(BackendGet.text(PATH, sessionManager))
}
