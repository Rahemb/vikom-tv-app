package com.example.tv_caller_app.ui.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import android.widget.ImageView
import com.example.tv_caller_app.R
import com.example.tv_caller_app.calling.permissions.PermissionHelper
import com.example.tv_caller_app.ui.activities.MainActivity
import com.example.tv_caller_app.ui.util.AvatarHelper
import com.example.tv_caller_app.viewmodel.ContactDetailViewModel
import com.example.tv_caller_app.viewmodel.ContactDetailViewModelFactory

class ContactDetailFragment : Fragment() {

    private val viewModel: ContactDetailViewModel by activityViewModels {
        ContactDetailViewModelFactory(requireActivity().application)
    }
    private lateinit var contactName: TextView
    private lateinit var contactUserId: TextView
    private lateinit var contactOnlineStatus: TextView
    private lateinit var btnGoBackBottom: TextView
    private lateinit var btnCall: TextView
    private lateinit var btnRemoveContact: TextView
    private lateinit var btnAddContact: TextView
    private lateinit var btnFavorite: TextView
    private lateinit var contactNickname: TextView

    
    private lateinit var permissionHelper: PermissionHelper
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionHelper.handlePermissionResult(permissions)
    }

    private var isContactOnline: Boolean = false

    companion object {
        private const val TAG = "ContactDetailFragment"
        private const val ARG_CONTACT_ID = "contact_id"
        private const val ARG_CONTACT_NAME = "contact_name"
        private const val ARG_CONTACT_USER_ID = "contact_user_id"
        private const val ARG_CONTACT_EMAIL = "contact_email"
        private const val ARG_IS_IN_CONTACTS = "is_in_contacts"
        private const val ARG_IS_FAVORITE = "is_favorite"
        private const val ARG_NICKNAME = "nickname"
        private const val ARG_AVATAR_URL = "avatar_url"

        fun newInstance(
            contactId: String,
            contactName: String,
            contactUserId: String,
            contactEmail: String? = null,
            isInContacts: Boolean = true,
            isFavorite: Boolean = false,
            nickname: String? = null,
            avatarUrl: String? = null
        ): ContactDetailFragment {
            val fragment = ContactDetailFragment()
            val args = Bundle()
            args.putString(ARG_CONTACT_ID, contactId)
            args.putString(ARG_CONTACT_NAME, contactName)
            args.putString(ARG_CONTACT_USER_ID, contactUserId)
            args.putString(ARG_CONTACT_EMAIL, contactEmail)
            args.putBoolean(ARG_IS_IN_CONTACTS, isInContacts)
            args.putBoolean(ARG_IS_FAVORITE, isFavorite)
            args.putString(ARG_NICKNAME, nickname)
            args.putString(ARG_AVATAR_URL, avatarUrl)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_contact_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (activity as? MainActivity)?.hideHeader()

        contactName = view.findViewById(R.id.contact_name)
        contactUserId = view.findViewById(R.id.contact_user_id)
        contactOnlineStatus = view.findViewById(R.id.contact_online_status)
        btnGoBackBottom = view.findViewById(R.id.btn_go_back_bottom)
        btnCall = view.findViewById(R.id.btn_call)
        btnRemoveContact = view.findViewById(R.id.btn_remove_contact)
        btnAddContact = view.findViewById(R.id.btn_add_contact)
        btnFavorite = view.findViewById(R.id.btn_favorite)
        contactNickname = view.findViewById(R.id.contact_nickname)

        permissionHelper = PermissionHelper(requireActivity()) { granted ->
            if (granted) {
                viewModel.callContact()
            } else {
                val errorMsg = permissionHelper.getPermissionErrorMessage()
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show()
                Log.w(TAG, "Permissions denied: $errorMsg")
            }
        }
        permissionHelper.registerPermissionLauncher(permissionLauncher)

        val id = arguments?.getString(ARG_CONTACT_ID) ?: ""
        val name = arguments?.getString(ARG_CONTACT_NAME) ?: ""
        val userIdStr = arguments?.getString(ARG_CONTACT_USER_ID) ?: ""
        val contactIdInt = userIdStr.toIntOrNull() ?: 0
        val email = arguments?.getString(ARG_CONTACT_EMAIL)
        val isInContacts = arguments?.getBoolean(ARG_IS_IN_CONTACTS, true) ?: true
        val isFavorite = arguments?.getBoolean(ARG_IS_FAVORITE, false) ?: false
        val nickname = arguments?.getString(ARG_NICKNAME)
        val avatarUrl = arguments?.getString(ARG_AVATAR_URL)

        viewModel.setContactDetails(id, name, contactIdInt, email, isFavorite, nickname, avatarUrl)

        val imgAvatar = view.findViewById<ImageView>(R.id.img_contact_avatar)
        AvatarHelper.loadAvatar(imgAvatar, avatarUrl, R.drawable.call_avatar_circle)

        val headerRow = view.findViewById<LinearLayout>(R.id.header_row)
        if (isInContacts) {
            headerRow.visibility = View.VISIBLE
            btnRemoveContact.visibility = View.VISIBLE
            btnAddContact.visibility = View.GONE
            btnFavorite.visibility = View.VISIBLE
            contactNickname.visibility = View.VISIBLE
        } else {
            headerRow.visibility = View.GONE
            btnRemoveContact.visibility = View.GONE
            btnAddContact.visibility = View.VISIBLE
            btnFavorite.visibility = View.GONE
            contactNickname.visibility = View.GONE
        }

        viewModel.checkOnlineStatus(contactIdInt)

        observeViewModel()
        setupClickListeners()

        btnCall.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? MainActivity)?.showHeader()
    }

    @SuppressLint("SetTextI18n")
    private fun observeViewModel() {
        viewModel.contactName.observe(viewLifecycleOwner) { name ->
            contactName.text = name
        }

        viewModel.userContactId.observe(viewLifecycleOwner) { contactId ->
            contactUserId.text = contactId.toString()
        }

        viewModel.callHistory.observe(viewLifecycleOwner) { history ->
            Log.d(TAG, "Call history updated: ${history.size} records")
        }

        viewModel.isCallInProgress.observe(viewLifecycleOwner) { inProgress ->
            if (inProgress) {
                val name = viewModel.contactName.value ?: getString(R.string.unknown_contact)
                val contactId = viewModel.userContactId.value ?: 0
                Toast.makeText(
                    requireContext(),
                    getString(R.string.calling_contact, name, contactId.toString()),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        viewModel.isFavorite.observe(viewLifecycleOwner) { isFavorite ->
            btnFavorite.text = if (isFavorite) "★" else "☆"
        }

        viewModel.nickname.observe(viewLifecycleOwner) { nickname ->
            if (!nickname.isNullOrBlank()) {
                contactNickname.text = nickname
                contactNickname.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black))
                contactNickname.setTypeface(null, android.graphics.Typeface.NORMAL)
            } else {
                contactNickname.text = "Legg til kallenavn"
                contactNickname.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                contactNickname.setTypeface(null, android.graphics.Typeface.ITALIC)
            }
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
            btnRemoveContact.isEnabled = !isLoading
            btnAddContact.isEnabled = !isLoading
            btnCall.isEnabled = !isLoading
            btnRemoveContact.alpha = if (isLoading) 0.5f else 1.0f
            btnAddContact.alpha = if (isLoading) 0.5f else 1.0f
            btnCall.alpha = if (isLoading) 0.5f else 1.0f
        }

        viewModel.isDeleted.observe(viewLifecycleOwner) { isDeleted ->
            if (isDeleted) {
                Log.d(TAG, "Contact deleted successfully")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.contact_deleted),
                    Toast.LENGTH_SHORT
                ).show()

                viewModel.resetDeletedState()

                requireActivity().supportFragmentManager.popBackStack()
            }
        }

        viewModel.isAdded.observe(viewLifecycleOwner) { isAdded ->
            if (isAdded) {
                Log.d(TAG, "Contact added successfully")
                Toast.makeText(
                    requireContext(),
                    getString(R.string.added_to_contacts),
                    Toast.LENGTH_SHORT
                ).show()

                viewModel.resetAddedState()

                btnAddContact.visibility = View.GONE
                btnRemoveContact.visibility = View.VISIBLE
            }
        }

        viewModel.presenceOnline.observe(viewLifecycleOwner) { isOnline ->
            val webrtcStatus = viewModel.presenceWebrtcStatus.value ?: "offline"
            isContactOnline = isOnline && webrtcStatus == "available"
            updateOnlineStatusUI(isOnline, webrtcStatus)
        }

        viewModel.presenceWebrtcStatus.observe(viewLifecycleOwner) { webrtcStatus ->
            val isOnline = viewModel.presenceOnline.value ?: false
            isContactOnline = isOnline && webrtcStatus == "available"
            updateOnlineStatusUI(isOnline, webrtcStatus)
        }
    }

    private fun setupClickListeners() {
        btnGoBackBottom.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        btnCall.setOnClickListener {
            handleCallButtonClick()
        }

        btnRemoveContact.setOnClickListener {
            showDeleteConfirmationDialog()
        }

        btnAddContact.setOnClickListener {
            showAddContactConfirmationDialog()
        }

        btnFavorite.setOnClickListener {
            viewModel.toggleFavorite()
        }

        contactNickname.setOnClickListener {
            showNicknameDialog()
        }
    }

    private fun showDeleteConfirmationDialog() {
        val name = viewModel.contactName.value ?: getString(R.string.unknown_contact)
        (activity as? MainActivity)?.showConfirmDialog(
            title = getString(R.string.delete_contact_title),
            message = getString(R.string.delete_contact_message, name),
            icon = "🗑️",
            onConfirm = {
                Log.d(TAG, "User confirmed deletion")
                viewModel.deleteContact()
            }
        )
    }

    private fun showAddContactConfirmationDialog() {
        val name = viewModel.contactName.value ?: getString(R.string.unknown_contact)
        (activity as? MainActivity)?.showConfirmDialog(
            title = getString(R.string.add_contact_title),
            message = getString(R.string.add_contact_message, name),
            icon = "➕",
            onConfirm = {
                Log.d(TAG, "User confirmed adding contact")
                viewModel.addToContacts()
            }
        )
    }

    private fun showNicknameDialog() {
        val editText = android.widget.EditText(requireContext()).apply {
            hint = "Kallenavn"
            setText(viewModel.nickname.value ?: "")
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Kallenavn")
            .setView(editText)
            .setPositiveButton("Lagre") { dialog, _ ->
                viewModel.updateNickname(editText.text.toString())
                dialog.dismiss()
            }
            .setNegativeButton("Avbryt") { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton("Fjern") { dialog, _ ->
                viewModel.updateNickname(null)
                dialog.dismiss()
            }
            .show()
    }

    private fun handleCallButtonClick() {
        if (!isContactOnline) {
            Toast.makeText(
                requireContext(),
                getString(R.string.calling_contact_offline),
                Toast.LENGTH_SHORT
            ).show()
        }

        if (permissionHelper.hasAllPermissions()) {
            viewModel.callContact()
        } else {
            permissionHelper.requestPermissions()
        }
    }

    private fun updateOnlineStatusUI(isOnline: Boolean, webrtcStatus: String) {
        when {
            !isOnline -> {
                contactOnlineStatus.text = getString(R.string.status_offline)
                contactOnlineStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            }
            webrtcStatus == "available" -> {
                contactOnlineStatus.text = getString(R.string.status_online)
                contactOnlineStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
            }
            webrtcStatus == "in_call" -> {
                contactOnlineStatus.text = getString(R.string.status_in_call)
                contactOnlineStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_orange_dark))
            }
            webrtcStatus == "busy" -> {
                contactOnlineStatus.text = getString(R.string.status_busy)
                contactOnlineStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
            }
            else -> {
                contactOnlineStatus.text = getString(R.string.status_offline)
                contactOnlineStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            }
        }
    }
}
