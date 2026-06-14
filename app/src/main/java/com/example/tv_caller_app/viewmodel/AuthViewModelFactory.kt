package com.example.tv_caller_app.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.repository.AuthRepository

class AuthViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AuthViewModel::class.java)) {
            val sessionManager = SessionManager.getInstance(application)
            val authDataSource = AuthRepository.getInstance(sessionManager)
            return AuthViewModel(application, authDataSource) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
