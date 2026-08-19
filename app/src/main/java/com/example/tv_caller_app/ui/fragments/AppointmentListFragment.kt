package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.ui.adapters.DayGroupedAppointmentAdapter
import com.example.tv_caller_app.viewmodel.AppointmentsUiState
import com.example.tv_caller_app.viewmodel.AppointmentsViewModel
import com.example.tv_caller_app.viewmodel.AppointmentsViewModelFactory
import java.time.LocalDateTime

/**
 * Every appointment, grouped by day: dates run down the left against a timeline
 * spine with the visits beside them. Sits behind the "Se alle avtaler" button on
 * [HomeFragment], which shows only the next one.
 *
 * Shares the activity-scoped [AppointmentsViewModel] with the hero, so returning
 * here costs no extra request beyond the usual resume refresh.
 */
class AppointmentListFragment : Fragment() {

    companion object {
        private const val TAG = "AppointmentListFragment"

        /**
         * Pushes the list over whatever is in the tab container, keeping the hero
         * on the back stack so BACK returns to it. Mirrors how the contacts tab
         * opens a contact's detail screen.
         */
        fun open(activity: FragmentActivity) {
            activity.supportFragmentManager
                .beginTransaction()
                .replace(R.id.main_browse_fragment, AppointmentListFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private val viewModel: AppointmentsViewModel by activityViewModels {
        AppointmentsViewModelFactory(requireActivity().application)
    }

    private lateinit var appointmentsList: RecyclerView
    private lateinit var txtState: TextView
    private lateinit var btnRetry: TextView
    private lateinit var btnBack: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_appointment_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appointmentsList = view.findViewById(R.id.appointments_list)
        txtState = view.findViewById(R.id.txt_state)
        btnRetry = view.findViewById(R.id.btn_retry)
        btnBack = view.findViewById(R.id.btn_screen_back)

        view.findViewById<TextView>(R.id.txt_screen_title)
            .setText(R.string.appointments_section_upcoming)

        appointmentsList.layoutManager = LinearLayoutManager(requireContext())
        btnRetry.setOnClickListener { viewModel.load() }
        btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        observeViewModel()

        appointmentsList.requestFocus()
    }

    /** Same reasoning as the hero: a stale medical appointment is the worse bug. */
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume - refreshing appointments")
        viewModel.load(forceRefresh = true)
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state -> render(state) }
    }

    private fun render(state: AppointmentsUiState) {
        when (state) {
            is AppointmentsUiState.Loading -> showMessage(R.string.appointments_loading, retry = false)
            is AppointmentsUiState.Empty -> showMessage(R.string.appointments_empty, retry = false)
            is AppointmentsUiState.NotLinked -> showMessage(R.string.appointments_not_linked, retry = false)
            is AppointmentsUiState.NotConfigured -> showMessage(R.string.appointments_not_configured, retry = false)
            is AppointmentsUiState.SessionExpired -> showMessage(R.string.appointments_session_expired, retry = true)
            is AppointmentsUiState.Error -> showMessage(R.string.appointments_error, retry = true)

            is AppointmentsUiState.Content -> {
                txtState.visibility = View.GONE
                btnRetry.visibility = View.GONE
                appointmentsList.visibility = View.VISIBLE
                appointmentsList.adapter = DayGroupedAppointmentAdapter(
                    DayGroupedAppointmentAdapter.build(state.appointments, LocalDateTime.now())
                )
            }
        }
    }

    private fun showMessage(messageRes: Int, retry: Boolean) {
        appointmentsList.visibility = View.GONE
        txtState.visibility = View.VISIBLE
        txtState.setText(messageRes)
        btnRetry.visibility = if (retry) View.VISIBLE else View.GONE
    }
}
