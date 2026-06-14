package com.example.tv_caller_app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.TVCallerApplication
import com.example.tv_caller_app.calling.permissions.PermissionHelper
import com.example.tv_caller_app.datasource.CallHistoryDataSource
import com.example.tv_caller_app.datasource.ContactDataSource
import com.example.tv_caller_app.datasource.PresenceDataSource
import com.example.tv_caller_app.model.CallHistory
import kotlinx.coroutines.launch

class ContactDetailViewModel(
    private val application: Application,
    private val callHistoryDataSource: CallHistoryDataSource,
    private val contactDataSource: ContactDataSource,
    private val presenceDataSource: PresenceDataSource
) : ViewModel() {
    companion object {
        private const val TAG = "ContactDetailViewModel"
    }

    private val _contactId = MutableLiveData<String>()
    val contactId: LiveData<String> = _contactId

    private val _contactName = MutableLiveData<String>()
    val contactName: LiveData<String> = _contactName

    private val _userContactId = MutableLiveData<Int>()
    val userContactId: LiveData<Int> = _userContactId

    private val _contactEmail = MutableLiveData<String?>()

    private val _callHistory = MutableLiveData<List<CallHistory>>()
    val callHistory: LiveData<List<CallHistory>> = _callHistory

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _isCallInProgress = MutableLiveData(false)
    val isCallInProgress: LiveData<Boolean> = _isCallInProgress

    private val _isDeleted = MutableLiveData(false)
    val isDeleted: LiveData<Boolean> = _isDeleted

    private val _isAdded = MutableLiveData(false)
    val isAdded: LiveData<Boolean> = _isAdded

    private val _isFavorite = MutableLiveData(false)
    val isFavorite: LiveData<Boolean> = _isFavorite

    private val _nickname = MutableLiveData<String?>()
    val nickname: LiveData<String?> = _nickname

    private val _presenceOnline = MutableLiveData(false)
    val presenceOnline: LiveData<Boolean> = _presenceOnline

    private val _presenceWebrtcStatus = MutableLiveData("offline")
    val presenceWebrtcStatus: LiveData<String> = _presenceWebrtcStatus

    private var avatarUrl: String? = null

    fun checkOnlineStatus(contactId: Int) {
        if (contactId == 0) {
            _presenceOnline.value = false
            _presenceWebrtcStatus.value = "offline"
            return
        }
        viewModelScope.launch {
            try {
                val result = presenceDataSource.getUserPresenceByContactId(contactId)
                if (result.isSuccess) {
                    val profile = result.getOrNull()
                    _presenceOnline.value = profile?.isOnline == true
                    _presenceWebrtcStatus.value = profile?.webrtcStatus ?: "offline"
                } else {
                    Log.w(TAG, "Failed to fetch online status: ${result.exceptionOrNull()?.message}")
                    _presenceOnline.value = false
                    _presenceWebrtcStatus.value = "offline"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking online status", e)
                _presenceOnline.value = false
                _presenceWebrtcStatus.value = "offline"
            }
        }
    }

    fun setContactDetails(id: String, name: String, contactId: Int, email: String? = null, isFavorite: Boolean = false, nickname: String? = null, avatarUrl: String? = null) {
        _contactId.value = id
        _contactName.value = name
        _userContactId.value = contactId
        _contactEmail.value = email
        _isFavorite.value = isFavorite
        _nickname.value = nickname
        this.avatarUrl = avatarUrl
        loadCallHistory(contactId)
    }

    

    private fun loadCallHistory(contactId: Int) {
        Log.d(TAG, "loadCallHistory() for contact ID $contactId")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                Log.d(TAG, "Fetching call history...")
                val history = callHistoryDataSource.getCallHistoryForContact(contactId)
                Log.d(TAG, "Successfully loaded ${history.size} call records")

                _callHistory.value = history
            } catch (e: Exception) {
                Log.e(TAG, "Error loading call history", e)
                _errorMessage.value = "Failed to load call history: ${e.message}"
                _callHistory.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun callContact() {
        val contactId = _userContactId.value
        val name = _contactName.value ?: "Unknown"

        if (contactId == null) {
            Log.e(TAG, "Cannot call: Invalid contact ID")
            _errorMessage.value = "Cannot initiate call: Invalid user ID"
            return
        }

        if (!PermissionHelper.hasBluetoothPermission(application)) {
            Log.w(TAG, "Bluetooth permission not granted — Bluetooth microphones may not work during call")
        }

        Log.d(TAG, "Initiating call to $name (Contact ID: $contactId)")
        _isCallInProgress.value = true

        viewModelScope.launch {
            try {
                val presenceResult = presenceDataSource.getUserPresenceByContactId(contactId)
                if (presenceResult.isFailure) {
                    Log.e(TAG, "Failed to look up profile for contact $contactId")
                    _errorMessage.value = "Cannot find user. They may be offline."
                    _isCallInProgress.value = false
                    return@launch
                }

                val profile = presenceResult.getOrNull()
                if (profile == null) {
                    Log.e(TAG, "Profile not found for contact $contactId")
                    _errorMessage.value = "User not found."
                    _isCallInProgress.value = false
                    return@launch
                }

                val profileId = profile.id
                Log.d(TAG, "Found profile UUID: $profileId for contact $contactId")

                val app = application as TVCallerApplication
                val callViewModel = app.getCallViewModel()

                if (callViewModel == null) {
                    Log.e(TAG, "CallViewModel not initialized")
                    _errorMessage.value = "Call service not available. Please try again."
                    _isCallInProgress.value = false
                    return@launch
                }

                callViewModel.initiateCall(profileId, name, contactId, _nickname.value, avatarUrl)

                kotlinx.coroutines.delay(500)
                _isCallInProgress.value = false

            } catch (e: Exception) {
                Log.e(TAG, "Error initiating call", e)
                e.printStackTrace()
                _errorMessage.value = "Failed to initiate call: ${e.message}"
                _isCallInProgress.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun deleteContact() {
        val id = _contactId.value
        val name = _contactName.value ?: "Unknown"

        if (id.isNullOrBlank()) {
            _errorMessage.value = "Cannot delete contact: Invalid contact ID"
            return
        }

        Log.d(TAG, "Deleting contact: $name (ID: $id)")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val success = contactDataSource.deleteContact(id)

                if (success) {
                    Log.d(TAG, "Contact deleted successfully: $name")
                    _isDeleted.value = true
                } else {
                    Log.e(TAG, "Failed to delete contact: $name")
                    _errorMessage.value = "Failed to delete contact. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting contact", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetDeletedState() {
        _isDeleted.value = false
    }

    fun addToContacts() {
        val contactId = _userContactId.value
        val name = _contactName.value ?: "Unknown"
        val email = _contactEmail.value

        if (contactId == null) {
            _errorMessage.value = "Cannot add contact: Invalid contact ID"
            return
        }

        Log.d(TAG, "Adding contact: $name (Contact ID: $contactId)")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val success = contactDataSource.createContact(
                    contactId = contactId,
                    email = email,
                    address = null,
                    notes = null,
                    isFavorite = false
                )

                if (success) {
                    Log.d(TAG, "Contact added successfully: $name")
                    _isAdded.value = true
                } else {
                    Log.e(TAG, "Failed to add contact: $name")
                    _errorMessage.value = "Failed to add contact. Please try again."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding contact", e)
                _errorMessage.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetAddedState() {
        _isAdded.value = false
    }

    fun toggleFavorite() {
        val id = _contactId.value
        val currentFavorite = _isFavorite.value ?: false

        if (id.isNullOrBlank()) {
            _errorMessage.value = "Cannot update favorite: Invalid contact"
            return
        }

        val newFavorite = !currentFavorite
        Log.d(TAG, "Toggling favorite for contact $id: $currentFavorite -> $newFavorite")

        viewModelScope.launch {
            try {
                val success = contactDataSource.updateFavoriteStatus(id, newFavorite)

                if (success) {
                    _isFavorite.value = newFavorite
                    Log.d(TAG, "Favorite updated successfully")
                } else {
                    _errorMessage.value = "Failed to update favorite"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling favorite", e)
                _errorMessage.value = "Error: ${e.message}"
            }
        }
    }

    fun updateNickname(nickname: String?) {
        val id = _contactId.value
        if (id.isNullOrBlank()) return

        val trimmed = nickname?.trim()?.ifBlank { null }
        Log.d(TAG, "Updating nickname for contact $id to '$trimmed'")

        viewModelScope.launch {
            try {
                val success = contactDataSource.updateNickname(id, trimmed)
                if (success) {
                    _nickname.value = trimmed
                    Log.d(TAG, "Nickname updated successfully")
                } else {
                    _errorMessage.value = "Failed to update nickname"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating nickname", e)
                _errorMessage.value = "Error: ${e.message}"
            }
        }
    }
}
