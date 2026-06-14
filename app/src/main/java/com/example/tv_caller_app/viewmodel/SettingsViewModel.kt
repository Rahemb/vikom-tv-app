package com.example.tv_caller_app.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.tv_caller_app.auth.SessionManager

class SettingsViewModel(private val sessionManager: SessionManager) : ViewModel() {

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val _sessionInfo = MutableLiveData<Map<String, String>>()
    val sessionInfo: LiveData<Map<String, String>> = _sessionInfo

    fun loadSessionInfo() {
        _sessionInfo.value = sessionManager.getSessionInfo()
    }
}
