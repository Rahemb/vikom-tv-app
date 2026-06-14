package com.example.tv_caller_app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.calling.repository.PresenceRepository
import com.example.tv_caller_app.repository.CallHistoryRepository
import com.example.tv_caller_app.repository.ContactRepository

class ContactDetailViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val sessionManager = SessionManager.getInstance(application)
        return ContactDetailViewModel(
            application,
            CallHistoryRepository.getInstance(sessionManager),
            ContactRepository.getInstance(sessionManager),
            PresenceRepository.getInstance(sessionManager)
        ) as T
    }
}
