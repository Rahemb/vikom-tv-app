package com.example.tv_caller_app.model

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * An appointment as returned by the backend's GET /api/tv/appointments/mine.
 *
 * Property names match the JSON exactly (the API serialises camelCase), so no
 * @SerialName annotations are needed. Only the fields this app displays are
 * modelled; the rest are dropped by ignoreUnknownKeys.
 *
 * Everything is nullable with a default so a field going missing degrades to an
 * empty row rather than throwing while parsing the whole list.
 */
@Serializable
data class Appointment(
    val id: Int = 0,
    val personnelName: String? = null,
    /** "yyyy-MM-dd" */
    val date: String? = null,
    /** "HH:mm" */
    val startTime: String? = null,
    /** "HH:mm" */
    val endTime: String? = null,
    /** Comma-separated, e.g. "Medisinhåndtering, Tilsyn" — not an array. */
    val tasks: String? = null,
    val availabilityNotes: String? = null,
    val status: String = "Booked"
)

/**
 * Parsing helpers. All tolerate malformed input by returning null — a bad date
 * from the backend must never crash the list.
 *
 * java.time on minSdk 21 is safe here because core library desugaring is enabled
 * (see isCoreLibraryDesugaringEnabled in build.gradle.kts).
 */
private fun Appointment.parseDate(): LocalDate? =
    runCatching { date?.let { LocalDate.parse(it) } }.getOrNull()

/**
 * The appointment's calendar day, or null if the date is missing or malformed.
 * Public because the day-grouped list keys its sections on it.
 */
fun Appointment.localDate(): LocalDate? = parseDate()

private fun parseTime(value: String?): LocalTime? =
    runCatching { value?.let { LocalTime.parse(it) } }.getOrNull()

fun Appointment.startDateTime(): LocalDateTime? {
    val d = parseDate() ?: return null
    val t = parseTime(startTime) ?: return null
    return LocalDateTime.of(d, t)
}

fun Appointment.endDateTime(): LocalDateTime? {
    val d = parseDate() ?: return null
    val t = parseTime(endTime) ?: return null
    return LocalDateTime.of(d, t)
}

/**
 * Whether the appointment still lies ahead. An unparseable date counts as
 * upcoming so a malformed row stays visible instead of silently vanishing into
 * the past section.
 */
fun Appointment.isUpcoming(now: LocalDateTime): Boolean {
    val end = endDateTime() ?: return true
    return !end.isBefore(now)
}
