package com.example.tv_caller_app.ui.fragments

import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.ui.adapters.ContactGridAdapter
import com.example.tv_caller_app.viewmodel.QuickDialViewModel
import com.example.tv_caller_app.viewmodel.QuickDialViewModelFactory

class QuickDialFragment : Fragment() {

    private val viewModel: QuickDialViewModel by activityViewModels {
        QuickDialViewModelFactory(requireActivity().application)
    }
    companion object {
        private const val TAG = "QuickDialFragment"
        private const val POLL_INTERVAL_MS = 10000L
    }

    private lateinit var contactsGrid: RecyclerView
    private lateinit var favoritesGrid: RecyclerView
    private var lastOnlineIds: Set<Int> = emptySet()
    private lateinit var txtStatus: TextView

    private val pollHandler = Handler(Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            viewModel.loadOnlineCount()
            pollHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d(TAG, "onCreateView called")
        return inflater.inflate(R.layout.fragment_quick_dial, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated called")

        quickDialAdapter = null
        favoritesAdapter = null

        contactsGrid = view.findViewById(R.id.contacts_grid)
        favoritesGrid = view.findViewById(R.id.favorites_grid)
        txtStatus = view.findViewById(R.id.txt_status)
        setupTabs(view)
        setupGrid()
        observeViewModel()

        view.findViewById<TextView>(R.id.tab_quick_dial)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume called - refreshing quick dial contacts and online status")
        viewModel.refreshQuickDialContacts()
        viewModel.loadOnlineCount()
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS)
    }

    override fun onPause() {
        super.onPause()
        pollHandler.removeCallbacks(pollRunnable)
    }

    private fun setupTabs(view: View) {
        val tabQuickDial = view.findViewById<TextView>(R.id.tab_quick_dial)
        val tabContacts = view.findViewById<TextView>(R.id.tab_contacts)

        tabQuickDial.setOnClickListener {
        }

        tabContacts.setOnClickListener {
            val activity = requireActivity()
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, AllContactsFragment())
                .runOnCommit {
                    activity.findViewById<android.widget.FrameLayout>(R.id.main_browse_fragment)?.post {
                        activity.findViewById<TextView>(R.id.tab_contacts)?.requestFocus()
                    }
                }
                .commit()
        }
    }

    private fun setupGrid() {
        val isLargeScreen = resources.getBoolean(R.bool.is_large_screen)
        val isTv = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        val columns = if (isLargeScreen || isTv) 4 else 3
        contactsGrid.layoutManager = GridLayoutManager(requireContext(), columns)
        favoritesGrid.layoutManager = GridLayoutManager(requireContext(), columns)
    }

    private var quickDialAdapter: ContactGridAdapter? = null
    private var favoritesAdapter: ContactGridAdapter? = null

    private fun updateContactGrids() {
        val onlineContacts = viewModel.onlineDisplayContacts.value ?: emptyList()
        val allFavorites = viewModel.allFavorites.value ?: emptyList()

        Log.d(TAG, "Online: ${onlineContacts.size}, Favorites: ${allFavorites.size}")

        
        if (onlineContacts.isNotEmpty()) {
            val existingQdAdapter = quickDialAdapter
            if (existingQdAdapter != null) {
                existingQdAdapter.updateContacts(onlineContacts)
            } else {
                val adapter = ContactGridAdapter(
                    onlineContacts,
                    ContactGridAdapter.CardType.QUICK_DIAL
                ) { contact -> navigateToContactDetail(contact) }
                quickDialAdapter = adapter
                contactsGrid.adapter = adapter
            }
        } else {
            quickDialAdapter = null
            contactsGrid.adapter = null
        }

        
        view?.findViewById<LinearLayout>(R.id.favorites_header_container)?.visibility = View.VISIBLE
        favoritesGrid.visibility = View.VISIBLE

        if (allFavorites.isNotEmpty()) {
            val existingFavAdapter = favoritesAdapter
            if (existingFavAdapter != null) {
                existingFavAdapter.updateContacts(allFavorites)
            } else {
                val adapter = ContactGridAdapter(
                    allFavorites,
                    ContactGridAdapter.CardType.FAVORITE
                ) { contact -> navigateToContactDetail(contact) }
                favoritesAdapter = adapter
                favoritesGrid.adapter = adapter
            }
        } else {
            favoritesAdapter = null
            favoritesGrid.adapter = null
        }
    }

    private fun observeViewModel() {
        viewModel.onlineDisplayContacts.observe(viewLifecycleOwner) {
            Log.d(TAG, "Online display contacts updated: ${it.size}")
            updateContactGrids()
        }

        viewModel.allFavorites.observe(viewLifecycleOwner) {
            Log.d(TAG, "All favorites updated: ${it.size}")
            updateContactGrids()
        }

        viewModel.onlineCount.observe(viewLifecycleOwner) {
            txtStatus.text = getString(R.string.status_logged_in)
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Log.w(TAG, "Error message: $it")
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state: $isLoading")
        }
    }

    private fun navigateToContactDetail(contact: com.example.tv_caller_app.model.Contact) {
        val detailFragment = ContactDetailFragment.newInstance(
            contactId = contact.id,
            contactName = contact.username,
            contactUserId = contact.contactId.toString(),
            contactEmail = contact.email,
            isInContacts = true,
            isFavorite = contact.isFavorite,
            nickname = contact.nickname,
            avatarUrl = contact.avatarUrl
        )
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, detailFragment)
            .addToBackStack(null)
            .commit()
    }
}
