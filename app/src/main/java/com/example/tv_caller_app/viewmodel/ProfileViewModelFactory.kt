package com.example.tv_caller_app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.repository.ProfileRepository

class ProfileViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val sessionManager = SessionManager.getInstance(application)
        return ProfileViewModel(ProfileRepository.getInstance(sessionManager), sessionManager) as T
    }
}
