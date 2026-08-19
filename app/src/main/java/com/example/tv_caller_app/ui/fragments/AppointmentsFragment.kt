package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.tv_caller_app.R
import com.example.tv_caller_app.model.Appointment
import com.example.tv_caller_app.model.isUpcoming
import com.example.tv_caller_app.model.localDate
import com.example.tv_caller_app.model.startDateTime
import com.example.tv_caller_app.ui.util.AppointmentStatusUi
import com.example.tv_caller_app.ui.util.MainTabsHelper
import com.example.tv_caller_app.viewmodel.AppointmentsUiState
import com.example.tv_caller_app.viewmodel.AppointmentsViewModel
import com.example.tv_caller_app.viewmodel.AppointmentsViewModelFactory
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The "Avtaler" tab: the patient's next appointment, shown large.
 *
 * The screen answers one question — who is coming, and when — so the start time
 * dominates and everything else is subordinate. The full history lives one step
 * away in [AppointmentListFragment], reachable from the card or the button.
 */
class AppointmentsFragment : Fragment() {

    companion object {
        private const val TAG = "AppointmentsFragment"
    }

    private val viewModel: AppointmentsViewModel by activityViewModels {
        AppointmentsViewModelFactory(requireActivity().application)
    }

    private lateinit var heroContainer: View
    private lateinit var heroCard: View
    private lateinit var txtEyebrow: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtUntil: TextView
    private lateinit var txtStatus: TextView
    private lateinit var txtPersonnel: TextView
    private lateinit var taskRow: LinearLayout
    private lateinit var txtNext: TextView
    private lateinit var btnSeeAll: TextView
    private lateinit var txtState: TextView
    private lateinit var btnRetry: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_appointments, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        heroContainer = view.findViewById(R.id.hero_container)
        heroCard = view.findViewById(R.id.hero_card)
        txtEyebrow = view.findViewById(R.id.txt_hero_eyebrow)
        txtTime = view.findViewById(R.id.txt_hero_time)
        txtUntil = view.findViewById(R.id.txt_hero_until)
        txtStatus = view.findViewById(R.id.txt_hero_status)
        txtPersonnel = view.findViewById(R.id.txt_hero_personnel)
        taskRow = view.findViewById(R.id.hero_tasks)
        txtNext = view.findViewById(R.id.txt_hero_next)
        btnSeeAll = view.findViewById(R.id.btn_see_all)
        txtState = view.findViewById(R.id.txt_state)
        btnRetry = view.findViewById(R.id.btn_retry)

        MainTabsHelper.bind(view, MainTabsHelper.Tab.APPOINTMENTS)

        btnRetry.setOnClickListener { viewModel.load() }
        heroCard.setOnClickListener { openFullList() }
        btnSeeAll.setOnClickListener { openFullList() }

        observeViewModel()

        view.findViewById<TextView>(R.id.tab_appointments)?.requestFocus()
    }

    /**
     * Always refetches. A Supabase broadcast is fire-and-forget with no replay, so
     * a change made while the tablet was asleep would otherwise not appear — and a
     * stale medical appointment is worse than an extra request.
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - refreshing appointments")
        viewModel.load(forceRefresh = true)
    }

    private fun openFullList() {
        AppointmentListFragment.open(requireActivity())
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state -> render(state) }
    }

    private fun render(state: AppointmentsUiState) {
        // Errors go in the state TextView rather than a Toast: a Toast is
        // unreadable on a tablet across the room and gone in three seconds.
        when (state) {
            is AppointmentsUiState.Loading -> showMessage(R.string.appointments_loading, retry = false)
            is AppointmentsUiState.Empty -> showMessage(R.string.appointments_empty, retry = false)
            is AppointmentsUiState.NotLinked -> showMessage(R.string.appointments_not_linked, retry = false)
            is AppointmentsUiState.NotConfigured -> showMessage(R.string.appointments_not_configured, retry = false)
            is AppointmentsUiState.SessionExpired -> showMessage(R.string.appointments_session_expired, retry = true)
            is AppointmentsUiState.Error -> showMessage(R.string.appointments_error, retry = true)

            is AppointmentsUiState.Content -> renderContent(state.appointments)
        }
    }

    /**
     * Picks the soonest upcoming appointment for the hero. When every appointment
     * is in the past the hero has nothing to show, but the full list still does —
     * so the message stays and the "Se alle avtaler" button stays with it.
     */
    private fun renderContent(appointments: List<Appointment>) {
        val now = LocalDateTime.now()
        val upcoming = appointments
            .filter { it.isUpcoming(now) }
            .sortedBy { it.startDateTime() ?: LocalDateTime.MAX }

        val next = upcoming.firstOrNull()
        if (next == null) {
            // Nothing ahead, but the past appointments are still worth reaching,
            // so the message replaces the card while the button stays put. The
            // card goes INVISIBLE rather than GONE so the weighted layout keeps
            // its shape and the button stays at the bottom.
            showMessage(R.string.appointments_none_upcoming, retry = false)
            heroContainer.visibility = View.VISIBLE
            heroCard.visibility = View.INVISIBLE
            txtNext.visibility = View.GONE
            return
        }

        txtState.visibility = View.GONE
        btnRetry.visibility = View.GONE
        heroContainer.visibility = View.VISIBLE
        heroCard.visibility = View.VISIBLE

        bindHero(next)
        bindNextLine(upcoming.getOrNull(1))
    }

    private fun bindHero(appointment: Appointment) {
        txtEyebrow.text = eyebrowFor(appointment.localDate())

        txtTime.text = appointment.startTime.orEmpty()

        val end = appointment.endTime
        if (end.isNullOrBlank()) {
            txtUntil.visibility = View.GONE
        } else {
            txtUntil.visibility = View.VISIBLE
            txtUntil.text = getString(R.string.appointment_hero_until, end)
        }

        txtStatus.setText(AppointmentStatusUi.labelRes(appointment.status))
        txtStatus.setBackgroundResource(AppointmentStatusUi.chipRes(appointment.status))

        val personnel = appointment.personnelName
        if (personnel.isNullOrBlank()) {
            txtPersonnel.visibility = View.GONE
        } else {
            txtPersonnel.visibility = View.VISIBLE
            txtPersonnel.text = personnel
        }

        bindTasks(appointment.tasks)
    }

    /** Splits the comma-separated tasks string into one pill per task. */
    private fun bindTasks(tasks: String?) {
        taskRow.removeAllViews()

        val labels = tasks.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        taskRow.visibility = if (labels.isEmpty()) View.GONE else View.VISIBLE

        val spacing = resources.getDimensionPixelSize(R.dimen.padding_standard) / 2
        val padH = resources.getDimensionPixelSize(R.dimen.padding_standard)
        val padV = spacing / 2

        labels.forEach { label ->
            val chip = TextView(requireContext()).apply {
                text = label
                // TextViewCompat, not setTextAppearance(int): the single-argument
                // overload is API 23 and this app supports 21.
                TextViewCompat.setTextAppearance(this, R.style.VK_Text_Body)
                setBackgroundResource(R.drawable.task_chip_background)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.vk_secondary))
                setPadding(padH, padV, padH, padV)
                gravity = Gravity.CENTER
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = spacing }

            taskRow.addView(chip, params)
        }
    }

    private fun bindNextLine(following: Appointment?) {
        if (following == null) {
            txtNext.visibility = View.GONE
            return
        }

        val date = AppointmentStatusUi.formatLongDate(following.date)
        val time = following.startTime.orEmpty()
        val who = following.personnelName
            ?.takeIf { it.isNotBlank() }
            ?.let { ", " + getString(R.string.appointment_personnel_format, it).lowercase() }
            .orEmpty()

        txtNext.visibility = View.VISIBLE
        txtNext.text = getString(R.string.appointment_next_label) + " $date $time$who"
    }

    /**
     * "I DAG" / "I MORGEN" for the two days a patient cares about most, and the
     * long date beyond that — an unparseable date falls back to the raw string.
     */
    private fun eyebrowFor(date: LocalDate?): String {
        val today = LocalDate.now()
        return when (date) {
            null -> ""
            today -> getString(R.string.appointment_hero_today)
            today.plusDays(1) -> getString(R.string.appointment_hero_tomorrow)
            else -> AppointmentStatusUi.formatLongDate(date.toString())
        }
    }

    private fun showMessage(messageRes: Int, retry: Boolean) {
        heroContainer.visibility = View.GONE
        txtState.visibility = View.VISIBLE
        txtState.setText(messageRes)
        btnRetry.visibility = if (retry) View.VISIBLE else View.GONE
    }
}
