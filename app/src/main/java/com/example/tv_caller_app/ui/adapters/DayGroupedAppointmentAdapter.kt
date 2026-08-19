package com.example.tv_caller_app.ui.adapters

import androidx.core.graphics.drawable.DrawableCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.model.Appointment
import com.example.tv_caller_app.model.isUpcoming
import com.example.tv_caller_app.model.localDate
import com.example.tv_caller_app.model.startDateTime
import com.example.tv_caller_app.ui.util.AppointmentStatusUi
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A row in the day-grouped list: the past divider, or one visit. */
sealed class DayGroupedItem {
    data class SectionHeader(@StringRes val titleRes: Int) : DayGroupedItem()

    /**
     * [startsDay] drives the date anchor and spine dot: only the first visit of a
     * day draws them, so several visits on one date read as a single block.
     */
    data class Visit(
        val appointment: Appointment,
        val startsDay: Boolean,
        val date: LocalDate?,
        val isPast: Boolean
    ) : DayGroupedItem()
}

/**
 * The full appointments list as a day-anchored timeline: dates run down the left
 * against a spine, visits sit to the right of it. Replaces the flat two-section
 * list — a patient who has lost track of the day gets the calendar back.
 */
class DayGroupedAppointmentAdapter(
    private val items: List<DayGroupedItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /**
     * Rows whose task text is expanded. Tapping a row toggles it — the task list
     * is clamped to two lines otherwise, and an elderly reader needs a way to see
     * the rest without a separate screen.
     */
    private val expanded = mutableSetOf<Int>()

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_VISIT = 1

        /**
         * Groups appointments by calendar day: upcoming days soonest first, then
         * a "Tidligere avtaler" divider, then past days most recent first.
         *
         * Cancelled appointments stay in their day rather than being hidden or
         * given a section of their own: a patient needs to see that tomorrow's
         * visit was cancelled, and the red badge carries that.
         *
         * Appointments with an unparseable date sort last within their bucket and
         * draw no anchor, so a malformed row stays visible without claiming a day.
         */
        fun build(
            appointments: List<Appointment>,
            now: LocalDateTime
        ): List<DayGroupedItem> {
            val (upcoming, past) = appointments.partition { it.isUpcoming(now) }

            val items = mutableListOf<DayGroupedItem>()

            items += flattenByDay(
                upcoming.sortedBy { it.startDateTime() ?: LocalDateTime.MAX },
                isPast = false
            )

            if (past.isNotEmpty()) {
                items += DayGroupedItem.SectionHeader(R.string.appointments_section_past)
                items += flattenByDay(
                    past.sortedByDescending { it.startDateTime() ?: LocalDateTime.MIN },
                    isPast = true
                )
            }

            return items
        }

        /** Marks the first visit of each day, preserving the order given. */
        private fun flattenByDay(
            sorted: List<Appointment>,
            isPast: Boolean
        ): List<DayGroupedItem> {
            var previousDate: LocalDate? = null
            var first = true

            return sorted.map { appointment ->
                val date = appointment.localDate()
                val startsDay = first || date != previousDate
                previousDate = date
                first = false

                DayGroupedItem.Visit(
                    appointment = appointment,
                    startsDay = startsDay,
                    date = date,
                    isPast = isPast
                )
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is DayGroupedItem.SectionHeader -> VIEW_TYPE_HEADER
        is DayGroupedItem.Visit -> VIEW_TYPE_VISIT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(
                inflater.inflate(R.layout.item_appointment_section_header, parent, false)
            )
        } else {
            VisitViewHolder(
                inflater.inflate(R.layout.item_appointment_day, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is DayGroupedItem.SectionHeader -> (holder as HeaderViewHolder).bind(item)
            is DayGroupedItem.Visit -> (holder as VisitViewHolder)
                .bind(item, expanded.contains(position))
        }
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.section_title)

        fun bind(item: DayGroupedItem.SectionHeader) {
            title.setText(item.titleRes)
        }
    }

    private inner class VisitViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dayLabel: TextView = view.findViewById(R.id.txt_day_label)
        private val dayNumber: TextView = view.findViewById(R.id.txt_day_number)
        private val dayMonth: TextView = view.findViewById(R.id.txt_day_month)
        private val dayDot: View = view.findViewById(R.id.day_dot)
        private val card: View = view.findViewById(R.id.visit_card)
        private val time: TextView = view.findViewById(R.id.txt_visit_time)
        private val personnel: TextView = view.findViewById(R.id.txt_visit_personnel)
        private val tasks: TextView = view.findViewById(R.id.txt_visit_tasks)
        private val status: TextView = view.findViewById(R.id.txt_visit_status)

        fun bind(item: DayGroupedItem.Visit, isExpanded: Boolean) {
            val context = itemView.context
            val appointment = item.appointment

            bindAnchor(item)

            time.text = appointment.startTime.orEmpty()

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

            card.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                if (!expanded.add(pos)) expanded.remove(pos)
                notifyItemChanged(pos)
            }
        }

        /**
         * The anchor is drawn once per day. Rows that continue a day keep the
         * views INVISIBLE rather than GONE so every row in the day stays the same
         * width and the spine runs straight.
         */
        private fun bindAnchor(item: DayGroupedItem.Visit) {
            val context = itemView.context
            val date = item.date

            if (!item.startsDay || date == null) {
                dayLabel.visibility = View.INVISIBLE
                dayNumber.visibility = View.INVISIBLE
                dayMonth.visibility = View.INVISIBLE
                dayDot.visibility = View.INVISIBLE
                return
            }

            dayLabel.visibility = View.VISIBLE
            dayNumber.visibility = View.VISIBLE
            dayMonth.visibility = View.VISIBLE
            dayDot.visibility = View.VISIBLE

            val today = LocalDate.now()
            dayLabel.text = when (date) {
                today -> context.getString(R.string.day_anchor_today)
                today.plusDays(1) -> context.getString(R.string.day_anchor_tomorrow)
                else -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
            }

            dayNumber.text = date.dayOfMonth.toString()
            dayMonth.text = date.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))

            val tint = ContextCompat.getColor(context, dayTint(date, today, item.isPast))
            dayLabel.setTextColor(tint)
            // mutate() so tinting one row's dot cannot recolour every other row
            // sharing the drawable's constant state.
            dayDot.background?.mutate()?.let { DrawableCompat.setTint(it, tint) }
        }

        /** Today and tomorrow earn a colour; everything else stays neutral. */
        @ColorRes
        private fun dayTint(date: LocalDate, today: LocalDate, isPast: Boolean): Int = when {
            isPast -> R.color.vk_gray_500
            date == today -> R.color.vk_success
            date == today.plusDays(1) -> R.color.vk_primary
            else -> R.color.vk_gray_500
        }
    }
}
