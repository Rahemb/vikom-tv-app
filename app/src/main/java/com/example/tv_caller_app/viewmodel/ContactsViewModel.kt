package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.Profile
import com.example.tv_caller_app.datasource.ContactDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ContactsViewModel(
    private val contactDataSource: ContactDataSource
) : ViewModel() {
    companion object {
        private const val TAG = "ContactsViewModel"
    }

    private var allContactsList: List<Contact> = emptyList()
    private var currentQuery: String = ""
    private var searchJob: Job? = null

    private val _displayedContacts = MutableLiveData<List<Contact>>()
    val displayedContacts: LiveData<List<Contact>> = _displayedContacts

    private val _searchResults = MutableLiveData<List<Profile>>()
    val searchResults: LiveData<List<Profile>> = _searchResults

    private val _isSearchingGlobal = MutableLiveData<Boolean>()

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    private val _contactAdded = MutableLiveData<Boolean>()
    val contactAdded: LiveData<Boolean> = _contactAdded

    fun loadAllContacts() {
        Log.d(TAG, "loadAllContacts() called")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                allContactsList = contactDataSource.getAllContacts()
                Log.d(TAG, "Loaded ${allContactsList.size} contacts")
                applyFilter()

                if (allContactsList.isEmpty()) {
                    _errorMessage.value = "No contacts found"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading contacts", e)
                _errorMessage.value = "Failed to load contacts: ${e.message}"
                _displayedContacts.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshContacts() {
        Log.d(TAG, "refreshContacts() called")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                allContactsList = contactDataSource.getAllContacts(forceRefresh = true)
                Log.d(TAG, "Refreshed ${allContactsList.size} contacts")
                applyFilter()
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing contacts", e)
                _errorMessage.value = "Failed to refresh contacts: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterContacts(query: String) {
        currentQuery = query
        applyFilter()

        searchJob?.cancel()
        if (query.isNotBlank()) {
            searchJob = viewModelScope.launch {
                delay(300)
                searchAllUsers(query)
            }
        } else {
            _searchResults.value = emptyList()
            _isSearchingGlobal.value = false
        }
    }

    private suspend fun searchAllUsers(query: String) {
        _isSearchingGlobal.value = true
        try {
            val results = contactDataSource.searchAllUsers(query)
            val contactIds = allContactsList.map { it.contactId }.toSet()
            val nonContacts = results.filter { it.contactId !in contactIds }
            _searchResults.value = nonContacts
            Log.d(TAG, "Global search: ${nonContacts.size} non-contact results")
        } catch (e: Exception) {
            Log.e(TAG, "Error searching all users", e)
            _searchResults.value = emptyList()
        } finally {
            _isSearchingGlobal.value = false
        }
    }

    private fun applyFilter() {
        val filtered = if (currentQuery.isBlank()) {
            allContactsList
        } else {
            allContactsList.filter {
                it.username.contains(currentQuery, ignoreCase = true) ||
                    it.contactId.toString().contains(currentQuery)
            }
        }
        _displayedContacts.value = filtered.sortedBy { it.username.lowercase() }
    }

    fun resetContactAdded() {
        _contactAdded.value = false
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun isContactInList(contactId: Int): Boolean {
        return allContactsList.any { it.contactId == contactId }
    }
}
