package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.CareTeamMember
import com.example.tv_caller_app.repository.PatientProfileRepository
import com.example.tv_caller_app.ui.adapters.CareTeamAdapter
import kotlinx.coroutines.launch

/**
 * Who looks after the patient, and how to reach them.
 *
 * Read-only. Personnel authenticate against the portal rather than Supabase, so
 * the tablet has no address to place a call to — a phone number the patient can
 * dial themselves is honest, where a Ring button that always failed would not be.
 */
class CareTeamFragment : Fragment() {

    companion object {
        private const val TAG = "CareTeamFragment"
    }

    private lateinit var list: RecyclerView
    private lateinit var txtState: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_care_team, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        list = view.findViewById(R.id.care_team_list)
        txtState = view.findViewById(R.id.txt_state)

        view.findViewById<TextView>(R.id.txt_screen_title).setText(R.string.care_team_title)
        view.findViewById<TextView>(R.id.btn_screen_back).setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        list.layoutManager = LinearLayoutManager(requireContext())

        load()
    }

    private fun load() {
        showMessage(R.string.appointments_loading)

        lifecycleScope.launch {
            val result = runCatching {
                PatientProfileRepository
                    .getInstance(SessionManager.getInstance(requireContext()))
                    .getMyCareTeam()
            }

            result
                .onSuccess { render(it) }
                .onFailure {
                    Log.w(TAG, "Could not load care team: ${it.message}")
                    showMessage(R.string.care_team_error)
                }
        }
    }

    private fun render(team: List<CareTeamMember>) {
        if (team.isEmpty()) {
            showMessage(R.string.care_team_empty)
            return
        }

        txtState.visibility = View.GONE
        list.visibility = View.VISIBLE
        list.adapter = CareTeamAdapter(team)
    }

    private fun showMessage(messageRes: Int) {
        list.visibility = View.GONE
        txtState.visibility = View.VISIBLE
        txtState.setText(messageRes)
    }
}
