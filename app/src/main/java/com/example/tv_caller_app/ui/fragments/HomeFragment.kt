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
import androidx.lifecycle.lifecycleScope
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.Appointment
import com.example.tv_caller_app.model.isUpcoming
import com.example.tv_caller_app.model.localDate
import com.example.tv_caller_app.model.startDateTime
import com.example.tv_caller_app.repository.PatientProfileRepository
import com.example.tv_caller_app.ui.util.AppointmentStatusUi
import com.example.tv_caller_app.viewmodel.AppointmentsUiState
import com.example.tv_caller_app.viewmodel.AppointmentsViewModel
import com.example.tv_caller_app.viewmodel.AppointmentsViewModelFactory
import com.example.tv_caller_app.viewmodel.ContactsViewModel
import com.example.tv_caller_app.viewmodel.ContactsViewModelFactory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The app's home screen, and the only screen with no way to get lost.
 *
 * The next visit takes the top half because it is why the tablet is in the room;
 * the three tiles below are everywhere else the app goes. There is no tab strip —
 * every destination is one press from here, and Back from any of them returns
 * here.
 */
class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private val appointmentsViewModel: AppointmentsViewModel by activityViewModels {
        AppointmentsViewModelFactory(requireActivity().application)
    }

    private val contactsViewModel: ContactsViewModel by activityViewModels {
        ContactsViewModelFactory(requireActivity().application)
    }

    private lateinit var heroCard: View
    private lateinit var txtEyebrow: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtWho: TextView
    private lateinit var txtStatus: TextView
    private lateinit var taskRow: LinearLayout
    private lateinit var txtHeroState: TextView

    private lateinit var tileAppointments: View
    private lateinit var tileContacts: View
    private lateinit var tileCareTeam: View
    private lateinit var txtAppointmentsSub: TextView
    private lateinit var txtContactsSub: TextView
    private lateinit var txtCareTeamSub: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        heroCard = view.findViewById(R.id.hero_card)
        txtEyebrow = view.findViewById(R.id.txt_hero_eyebrow)
        txtTime = view.findViewById(R.id.txt_hero_time)
        txtWho = view.findViewById(R.id.txt_hero_who)
        txtStatus = view.findViewById(R.id.txt_hero_status)
        taskRow = view.findViewById(R.id.hero_tasks)
        txtHeroState = view.findViewById(R.id.txt_hero_state)

        tileAppointments = view.findViewById(R.id.tile_appointments)
        tileContacts = view.findViewById(R.id.tile_contacts)
        tileCareTeam = view.findViewById(R.id.tile_care_team)
        txtAppointmentsSub = view.findViewById(R.id.txt_tile_appointments_sub)
        txtContactsSub = view.findViewById(R.id.txt_tile_contacts_sub)
        txtCareTeamSub = view.findViewById(R.id.txt_tile_care_team_sub)

        // The hero and the appointments tile lead to the same place: a patient who
        // presses the visit itself means "tell me more about this".
        heroCard.setOnClickListener { open(AppointmentListFragment()) }
        tileAppointments.setOnClickListener { open(AppointmentListFragment()) }
        tileContacts.setOnClickListener { open(AllContactsFragment()) }
        tileCareTeam.setOnClickListener { open(CareTeamFragment()) }

        observeViewModels()
        loadCareTeamCount()

        heroCard.requestFocus()
    }

    /**
     * Always refetches. A Supabase broadcast is fire-and-forget with no replay, so
     * a change made while the tablet was asleep would otherwise not appear — and a
     * stale medical appointment is worse than an extra request.
     */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - refreshing appointments")
        appointmentsViewModel.load(forceRefresh = true)

        if (contactsViewModel.displayedContacts.value.isNullOrEmpty()) {
            contactsViewModel.loadAllContacts()
        }
    }

    private fun open(fragment: Fragment) {
        requireActivity().supportFragmentManager
            .beginTransaction()
            .replace(R.id.main_browse_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun observeViewModels() {
        appointmentsViewModel.uiState.observe(viewLifecycleOwner) { render(it) }

        contactsViewModel.displayedContacts.observe(viewLifecycleOwner) { contacts ->
            txtContactsSub.text =
                getString(R.string.home_contacts_count, contacts?.size ?: 0)
        }
    }

    private fun loadCareTeamCount() {
        lifecycleScope.launch {
            val team = runCatching {
                PatientProfileRepository
                    .getInstance(SessionManager.getInstance(requireContext()))
                    .getMyCareTeam()
            }.getOrElse {
                Log.w(TAG, "Could not load care team: ${it.message}")
                emptyList()
            }

            // The first name rather than a count: "Nora Nilsen" tells a patient more
            // than "1 person" does.
            txtCareTeamSub.text = team.firstOrNull()?.fullName
                ?: getString(R.string.home_care_team_empty)
        }
    }

    private fun render(state: AppointmentsUiState) {
        when (state) {
            is AppointmentsUiState.Loading -> heroMessage(R.string.appointments_loading)
            is AppointmentsUiState.Empty -> heroMessage(R.string.appointments_empty)
            is AppointmentsUiState.NotLinked -> heroMessage(R.string.appointments_not_linked)
            is AppointmentsUiState.NotConfigured -> heroMessage(R.string.appointments_not_configured)
            is AppointmentsUiState.SessionExpired -> heroMessage(R.string.appointments_session_expired)
            is AppointmentsUiState.Error -> heroMessage(R.string.appointments_error)

            is AppointmentsUiState.Content -> renderContent(state.appointments)
        }
    }

    private fun renderContent(appointments: List<Appointment>) {
        val now = LocalDateTime.now()
        val upcoming = appointments
            .filter { it.isUpcoming(now) }
            .sortedBy { it.startDateTime() ?: LocalDateTime.MAX }

        txtAppointmentsSub.text =
            getString(R.string.home_appointments_count, upcoming.size)

        val next = upcoming.firstOrNull()
        if (next == null) {
            heroMessage(R.string.appointments_none_upcoming)
            return
        }

        showHeroFields(true)
        txtHeroState.visibility = View.GONE

        txtEyebrow.text = eyebrowFor(next.localDate())
        txtTime.text = next.startTime.orEmpty()

        val who = next.personnelName
        txtWho.text = if (who.isNullOrBlank()) {
            ""
        } else {
            getString(R.string.appointment_personnel_format, who)
        }

        txtStatus.setText(AppointmentStatusUi.labelRes(next.status))
        txtStatus.setBackgroundResource(AppointmentStatusUi.chipRes(next.status))

        bindTasks(next.tasks)
    }

    /** Splits the comma-separated tasks string into one pill per task. */
    private fun bindTasks(tasks: String?) {
        taskRow.removeAllViews()

        val labels = tasks.orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val padH = resources.getDimensionPixelSize(R.dimen.chip_padding_horizontal)
        val padV = resources.getDimensionPixelSize(R.dimen.chip_padding_vertical)
        val gap = resources.getDimensionPixelSize(R.dimen.chip_gap)

        labels.forEach { label ->
            val chip = TextView(requireContext()).apply {
                text = label
                // Caption rather than body: the same size the appointment list gives
                // tasks, and a badge that matched the personnel name would compete
                // with it. Dark on grey-200 mirrors the portal's neutral badge.
                TextViewCompat.setTextAppearance(this, R.style.VK_Text_Caption)
                setBackgroundResource(R.drawable.task_chip_background)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.vk_text_primary))
                setPadding(padH, padV, padH, padV)
                gravity = Gravity.CENTER
                maxLines = 1
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = gap }

            taskRow.addView(chip, params)
        }
    }

    /**
     * "I DAG" / "I MORGEN" for the two days a patient cares about most, and the
     * long date beyond that.
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

    /**
     * Replaces the visit details with one line. The card keeps its size and its
     * focus, so the tiles below never jump and the D-pad never loses its place.
     */
    private fun heroMessage(messageRes: Int) {
        showHeroFields(false)
        txtHeroState.visibility = View.VISIBLE
        txtHeroState.setText(messageRes)
    }

    private fun showHeroFields(visible: Boolean) {
        val flag = if (visible) View.VISIBLE else View.GONE
        txtEyebrow.visibility = flag
        txtTime.visibility = flag
        txtWho.visibility = flag
        txtStatus.visibility = flag
        taskRow.visibility = flag
    }
}
