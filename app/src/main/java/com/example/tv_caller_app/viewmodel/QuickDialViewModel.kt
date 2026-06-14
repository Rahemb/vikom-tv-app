package com.example.tv_caller_app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.datasource.ContactDataSource
import com.example.tv_caller_app.datasource.PresenceDataSource
import com.example.tv_caller_app.datasource.QuickDialDataSource
import com.example.tv_caller_app.model.Contact
import kotlinx.coroutines.launch

class QuickDialViewModel(
    private val quickDialDataSource: QuickDialDataSource,
    private val presenceDataSource: PresenceDataSource,
    private val contactDataSource: ContactDataSource
) : ViewModel() {
    companion object {
        private const val TAG = "QuickDialViewModel"
    }

    private val _allFavorites = MutableLiveData<List<Contact>>(emptyList())
    val allFavorites: LiveData<List<Contact>> = _allFavorites

    private val _onlineDisplayContacts = MutableLiveData<List<Contact>>(emptyList())
    val onlineDisplayContacts: LiveData<List<Contact>> = _onlineDisplayContacts

    private val _onlineCount = MutableLiveData(0)
    val onlineCount: LiveData<Int> = _onlineCount

    private val _onlineContactIds = MutableLiveData<Set<Int>>(emptySet())

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun refreshQuickDialContacts() {
        Log.d(TAG, "refreshQuickDialContacts() called")
        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val allContacts = contactDataSource.getAllContacts(forceRefresh = true)
                _allFavorites.value = allContacts.filter { it.isFavorite }
                Log.d(TAG, "All favorites: ${_allFavorites.value?.size}")

                val entries = quickDialDataSource.getQuickDialEntries(forceRefresh = true)
                _onlineDisplayContacts.value = entries.map { it.contact }
                Log.d(TAG, "Quick dial entries: ${entries.size}")

                updateOnlineCount(allContacts)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing contacts", e)
                _errorMessage.value = "Failed to refresh contacts: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadOnlineCount() {
        viewModelScope.launch {
            try {
                val entries = quickDialDataSource.getQuickDialEntries(forceRefresh = true)
                _onlineDisplayContacts.value = entries.map { it.contact }

                val allContacts = contactDataSource.getAllContacts()
                updateOnlineCount(allContacts)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading online count", e)
                _onlineCount.value = 0
            }
        }
    }

    private suspend fun updateOnlineCount(allContacts: List<Contact>) {
        val result = presenceDataSource.getOnlineUsers()
        result.onSuccess { onlineUsers ->
            val myIds = allContacts.map { it.contactId }.toSet()
            val onlineIds = onlineUsers.filter { it.contactId in myIds }.map { it.contactId }.toSet()
            _onlineContactIds.value = onlineIds
            _onlineCount.value = onlineIds.size
            Log.d(TAG, "Online contacts: ${onlineIds.size}")
        }
        result.onFailure {
            _onlineContactIds.value = emptySet()
            _onlineCount.value = 0
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
