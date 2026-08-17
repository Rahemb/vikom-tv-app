package com.example.tv_caller_app.ui.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.tv_caller_app.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shared presentation rules for appointments, used by both the list and the push
 * notification screen so the two cannot drift apart.
 */
object AppointmentStatusUi {

    /**
     * Patient-facing label for a backend status. Unknown values fall back to a
     * neutral label rather than throwing, so a new status added server-side can
     * never crash the screen.
     */
    @StringRes
    fun labelRes(status: String?): Int = when (status) {
        "Booked" -> R.string.appointment_status_booked
        "InProgress" -> R.string.appointment_status_inprogress
        "Completed" -> R.string.appointment_status_completed
        "NotCompleted" -> R.string.appointment_status_notcompleted
        "Cancelled" -> R.string.appointment_status_cancelled
        else -> R.string.appointment_title_default
    }

    @DrawableRes
    fun chipRes(status: String?): Int = when (status) {
        "Booked" -> R.drawable.status_chip_booked
        "Completed" -> R.drawable.status_chip_completed
        "Cancelled" -> R.drawable.status_chip_cancelled
        else -> R.drawable.status_chip_neutral
    }

    /** Label for a realtime event's action field. */
    @StringRes
    fun actionLabelRes(action: String?): Int = when (action?.lowercase()) {
        "created" -> R.string.appointment_action_created
        "updated" -> R.string.appointment_action_updated
        "cancelled" -> R.string.appointment_action_cancelled
        else -> R.string.appointment_title_default
    }

    /**
     * Formats "2026-08-20" as "torsdag 20. august" in the app's current language.
     *
     * Locale.getDefault() is correct here because each Activity calls applyLocale()
     * from SettingsManager on startup. Returns the raw string unchanged if it does
     * not parse, so a malformed value degrades rather than crashing.
     */
    fun formatLongDate(isoDate: String?): String {
        if (isoDate.isNullOrBlank()) return ""

        return runCatching {
            LocalDate.parse(isoDate)
                .format(DateTimeFormatter.ofPattern("EEEE d. MMMM", Locale.getDefault()))
        }.getOrDefault(isoDate)
    }
}
