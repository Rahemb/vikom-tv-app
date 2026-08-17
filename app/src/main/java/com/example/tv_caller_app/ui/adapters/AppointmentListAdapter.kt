package com.example.tv_caller_app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.model.Appointment
import com.example.tv_caller_app.model.isUpcoming
import com.example.tv_caller_app.model.startDateTime
import com.example.tv_caller_app.ui.util.AppointmentStatusUi
import java.time.LocalDateTime

/** A row in the appointments list: either a section header or an appointment. */
sealed class AppointmentListItem {
    data class SectionHeader(@StringRes val titleRes: Int) : AppointmentListItem()
    data class Entry(val appointment: Appointment) : AppointmentListItem()
}

/**
 * Renders the grouped appointments list. Structured like [ContactListAdapter]:
 * two view types and a companion that builds the flattened item list.
 */
class AppointmentListAdapter(
    private val items: List<AppointmentListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /**
     * Rows whose task text is expanded. Tapping a row toggles it — the task list
     * is clamped to two lines otherwise, and an elderly reader needs a way to see
     * the rest without a separate screen.
     */
    private val expanded = mutableSetOf<Int>()

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_APPOINTMENT = 1

        /**
         * Splits appointments into upcoming and past, each under its own header.
         * Headers are only emitted for non-empty groups.
         *
         * Cancelled appointments stay in their time bucket rather than being
         * hidden or given a section of their own: a patient needs to see that
         * tomorrow's visit was cancelled, and the red badge carries that.
         */
        fun buildGroupedList(
            appointments: List<Appointment>,
            now: LocalDateTime
        ): List<AppointmentListItem> {
            val (upcoming, past) = appointments.partition { it.isUpcoming(now) }

            val items = mutableListOf<AppointmentListItem>()

            if (upcoming.isNotEmpty()) {
                items += AppointmentListItem.SectionHeader(R.string.appointments_section_upcoming)
                // Soonest first.
                items += upcoming
                    .sortedBy { it.startDateTime() ?: LocalDateTime.MAX }
                    .map { AppointmentListItem.Entry(it) }
            }

            if (past.isNotEmpty()) {
                items += AppointmentListItem.SectionHeader(R.string.appointments_section_past)
                // Most recent first.
                items += past
                    .sortedByDescending { it.startDateTime() ?: LocalDateTime.MIN }
                    .map { AppointmentListItem.Entry(it) }
            }

            return items
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is AppointmentListItem.SectionHeader -> VIEW_TYPE_HEADER
        is AppointmentListItem.Entry -> VIEW_TYPE_APPOINTMENT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(
                inflater.inflate(R.layout.item_appointment_section_header, parent, false)
            )
        } else {
            AppointmentViewHolder(
                inflater.inflate(R.layout.item_appointment, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is AppointmentListItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is AppointmentListItem.Entry -> (holder as AppointmentViewHolder)
                .bind(item.appointment, expanded.contains(position))
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.section_title)

        fun bind(item: AppointmentListItem.SectionHeader) {
            title.setText(item.titleRes)
        }
    }

    private inner class AppointmentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val date: TextView = view.findViewById(R.id.txt_appointment_date)
        private val time: TextView = view.findViewById(R.id.txt_appointment_time)
        private val personnel: TextView = view.findViewById(R.id.txt_appointment_personnel)
        private val tasks: TextView = view.findViewById(R.id.txt_appointment_tasks)
        private val status: TextView = view.findViewById(R.id.txt_appointment_status)

        fun bind(appointment: Appointment, isExpanded: Boolean) {
            val context = itemView.context

            date.text = AppointmentStatusUi.formatLongDate(appointment.date)

            time.text = context.getString(
                R.string.appointment_time_format,
                appointment.startTime.orEmpty(),
                appointment.endTime.orEmpty()
            )

            personnel.text = if (appointment.personnelName.isNullOrBlank()) {
                ""
            } else {
                context.getString(R.string.appointment_personnel_format, appointment.personnelName)
            }
            personnel.visibility = if (personnel.text.isNullOrBlank()) View.GONE else View.VISIBLE

            tasks.text = appointment.tasks.orEmpty()
            tasks.visibility = if (appointment.tasks.isNullOrBlank()) View.GONE else View.VISIBLE
            tasks.maxLines = if (isExpanded) Int.MAX_VALUE else 2

            status.setText(AppointmentStatusUi.labelRes(appointment.status))
            status.setBackgroundResource(AppointmentStatusUi.chipRes(appointment.status))

            itemView.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                if (!expanded.add(pos)) expanded.remove(pos)
                notifyItemChanged(pos)
            }
        }
    }
}
