package com.example.tv_caller_app.model

import kotlinx.serialization.Serializable

/**
 * The signed-in patient as the backend knows them, from GET /api/tv/me.
 *
 * The Supabase profile only carries a username ("ingrid.berg"); the real name
 * lives in the backend's user record, which is why the greeting needs this call.
 */
@Serializable
data class PatientProfile(
    val fullName: String = "",
    val userName: String = ""
)

/**
 * Someone who looks after the patient, from GET /api/tv/careteam/mine.
 *
 * Read-only: personnel have no Supabase profile, so they cannot be added as
 * contacts or called from the tablet. This exists so a patient can see who cares
 * for them and reach them by telephone.
 */
@Serializable
data class CareTeamMember(
    val fullName: String = "",
    /** "Personnel" or "Relative". */
    val relationshipType: String = "",
    val phoneNumber: String? = null
)
