package com.example.tv_caller_app.datasource

import com.example.tv_caller_app.model.Appointment

/**
 * Read access to the signed-in patient's own appointments.
 *
 * Implemented by AppointmentRepository against the .NET backend. Kept as an
 * interface so the ViewModel can be exercised with a fake, matching the other
 * datasource contracts in this package.
 */
interface AppointmentDataSource {

    /**
     * Returns the patient's appointments, newest data first from the network
     * unless a recent cached copy is available.
     *
     * @throws ProfileNotLinkedException when no patient record is linked to this
     *   Supabase profile — a setup problem, not an empty list.
     * @throws SessionExpiredException when the Supabase session can no longer be
     *   refreshed and the user must sign in again.
     */
    suspend fun getMyAppointments(forceRefresh: Boolean = false): List<Appointment>

    fun invalidateCache()
}

/** No backend patient is mapped to the signed-in Supabase profile. */
class ProfileNotLinkedException :
    Exception("No patient is linked to this Supabase profile")

/** The Supabase session is no longer valid and could not be refreshed. */
class SessionExpiredException :
    Exception("Supabase session expired")

/** The backend address is not set in local.properties. */
class BackendNotConfiguredException :
    Exception("backend.base.url is not set in local.properties")
