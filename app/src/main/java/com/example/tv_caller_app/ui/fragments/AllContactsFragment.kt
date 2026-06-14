package com.example.tv_caller_app.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.Profile
import com.example.tv_caller_app.ui.adapters.ContactListAdapter
import com.example.tv_caller_app.viewmodel.ContactsViewModel
import com.example.tv_caller_app.viewmodel.ContactsViewModelFactory

class AllContactsFragment : Fragment() {

    companion object {
        private const val TAG = "AllContactsFragment"
    }

    private val viewModel: ContactsViewModel by activityViewModels {
        ContactsViewModelFactory(requireActivity().application)
    }
    private lateinit var contactsList: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var btnClearSearch: TextView
    private lateinit var btnSearch: TextView

    private var currentContacts: List<Contact> = emptyList()
    private var currentSearchResults: List<Profile> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_contacts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        contactsList = view.findViewById(R.id.contacts_list)
        searchInput = view.findViewById(R.id.search_input)
        btnClearSearch = view.findViewById(R.id.btn_clear_search)
        btnSearch = view.findViewById(R.id.btn_search)

        setupTabs(view)
        setupList()
        setupSearch()
        observeViewModel()

        if (viewModel.displayedContacts.value.isNullOrEmpty()) {
            viewModel.loadAllContacts()
        }

        view.findViewById<TextView>(R.id.tab_contacts)?.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshContacts()
    }

    private fun setupTabs(view: View) {
        val tabQuickDial = view.findViewById<TextView>(R.id.tab_quick_dial)
        val tabContacts = view.findViewById<TextView>(R.id.tab_contacts)

        tabQuickDial.setOnClickListener {
            val activity = requireActivity()
            activity.supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, QuickDialFragment())
                .runOnCommit {
                    activity.findViewById<android.widget.FrameLayout>(R.id.main_browse_fragment)?.post {
                        activity.findViewById<TextView>(R.id.tab_quick_dial)?.requestFocus()
                    }
                }
                .commit()
        }

        tabContacts.setOnClickListener {
        }
    }

    private fun setupList() {
        contactsList.layoutManager = LinearLayoutManager(requireContext())

        contactsList.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.setOnKeyListener { v, keyCode, event ->
                    if (event.action == android.view.KeyEvent.ACTION_DOWN &&
                        keyCode == android.view.KeyEvent.KEYCODE_DPAD_UP) {
                        val lm = contactsList.layoutManager as? LinearLayoutManager
                        val firstVisible = lm?.findFirstVisibleItemPosition() ?: 1
                        val pos = contactsList.getChildAdapterPosition(v)
                        if (firstVisible <= 1 && pos <= 2) {
                            contactsList.smoothScrollToPosition(0)
                            searchInput.requestFocus()
                            return@setOnKeyListener true
                        }
                    }
                    false
                }
            }
            override fun onChildViewDetachedFromWindow(view: View) {
                view.setOnKeyListener(null)
            }
        })
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                viewModel.filterContacts(s?.toString() ?: "")
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            searchInput.text.clear()
        }

        btnSearch.setOnClickListener {
            val query = searchInput.text.toString()
            viewModel.filterContacts(query)
            searchInput.clearFocus()
        }
    }

    private fun updateAdapter() {
        val hasQuery = searchInput.text.isNotBlank()

        val items = if (hasQuery && currentSearchResults.isNotEmpty()) {
            ContactListAdapter.buildCombinedList(currentContacts, currentSearchResults, true)
        } else {
            ContactListAdapter.buildGroupedList(currentContacts)
        }

        val adapter = ContactListAdapter(
            items = items,
            onContactClick = { contact -> navigateToContactDetail(contact) },
            onSearchResultClick = { profile -> navigateToSearchResultDetail(profile) }
        )
        contactsList.adapter = adapter
    }

    private fun navigateToContactDetail(contact: Contact) {
        val isInContacts = viewModel.isContactInList(contact.contactId)
        val detailFragment = ContactDetailFragment.newInstance(
            contactId = contact.id,
            contactName = contact.username,
            contactUserId = contact.contactId.toString(),
            contactEmail = contact.email,
            isInContacts = isInContacts,
            isFavorite = contact.isFavorite,
            nickname = contact.nickname,
            avatarUrl = contact.avatarUrl
        )
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun navigateToSearchResultDetail(profile: Profile) {
        val detailFragment = ContactDetailFragment.newInstance(
            contactId = "",
            contactName = profile.username ?: "Unknown",
            contactUserId = profile.contactId.toString(),
            contactEmail = profile.email,
            isInContacts = false,
            isFavorite = false,
            avatarUrl = profile.avatarUrl
        )
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.main_browse_fragment, detailFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun observeViewModel() {
        viewModel.displayedContacts.observe(viewLifecycleOwner) { contacts ->
            Log.d(TAG, "Contacts updated: ${contacts.size}")
            currentContacts = contacts
            updateAdapter()
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { results ->
            Log.d(TAG, "Search results updated: ${results.size}")
            currentSearchResults = results
            updateAdapter()
        }

        viewModel.contactAdded.observe(viewLifecycleOwner) { added ->
            if (added) {
                Toast.makeText(requireContext(), getString(R.string.contact_added), Toast.LENGTH_SHORT).show()
                viewModel.resetContactAdded()
            }
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            error?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            Log.d(TAG, "Loading state: $isLoading")
        }
    }
}
