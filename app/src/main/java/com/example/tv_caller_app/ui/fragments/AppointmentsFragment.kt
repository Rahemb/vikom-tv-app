package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.ui.adapters.AppointmentListAdapter
import com.example.tv_caller_app.ui.util.MainTabsHelper
import com.example.tv_caller_app.viewmodel.AppointmentsUiState
import com.example.tv_caller_app.viewmodel.AppointmentsViewModel
import com.example.tv_caller_app.viewmodel.AppointmentsViewModelFactory

/**
 * The "Avtaler" tab: the patient's own upcoming and past appointments, pulled
 * from the backend.
 */
class AppointmentsFragment : Fragment() {

    companion object {
        private const val TAG = "AppointmentsFragment"
    }

    private val viewModel: AppointmentsViewModel by activityViewModels {
        AppointmentsViewModelFactory(requireActivity().application)
    }

    private lateinit var appointmentsList: RecyclerView
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

        appointmentsList = view.findViewById(R.id.appointments_list)
        txtState = view.findViewById(R.id.txt_state)
        btnRetry = view.findViewById(R.id.btn_retry)

        MainTabsHelper.bind(view, MainTabsHelper.Tab.APPOINTMENTS)

        appointmentsList.layoutManager = LinearLayoutManager(requireContext())
        btnRetry.setOnClickListener { viewModel.load() }

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

            is AppointmentsUiState.Content -> {
                txtState.visibility = View.GONE
                btnRetry.visibility = View.GONE
                appointmentsList.visibility = View.VISIBLE
                appointmentsList.adapter = AppointmentListAdapter(state.items)
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
