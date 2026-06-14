package com.example.tv_caller_app

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.auth.SessionRefreshManager
import com.example.tv_caller_app.calling.repository.PresenceRepository
import com.example.tv_caller_app.repository.AuthRepository
import com.example.tv_caller_app.repository.CallHistoryRepository
import com.example.tv_caller_app.repository.ContactRepository
import com.example.tv_caller_app.repository.QuickDialRepository
import com.example.tv_caller_app.calling.service.CallNotificationManager
import com.example.tv_caller_app.calling.service.SignalingForegroundService
import com.example.tv_caller_app.calling.signaling.SignalingManager
import com.example.tv_caller_app.calling.webrtc.WebRTCManager
import com.example.tv_caller_app.model.FcmCallData
import com.example.tv_caller_app.repository.FCMRepository
import com.example.tv_caller_app.viewmodel.CallViewModel
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TVCallerApplication : Application(), DefaultLifecycleObserver {

    private val TAG = "TVCallerApplication"
    private var sessionRefreshManager: SessionRefreshManager? = null
    private var presenceRepository: PresenceRepository? = null
    private var callViewModel: CallViewModel? = null
    var pendingFcmCallData: FcmCallData? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super<Application>.onCreate()
        Log.d(TAG, "TVCallerApplication starting...")

        initializeSessionManager()

        initializeSessionRefresh()

        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        Log.d(TAG, "TVCallerApplication initialized successfully")
    }

    fun startSessionRefresh() {
        try {
            if (sessionRefreshManager == null) {
                val sessionManager = SessionManager.getInstance(this)
                val authRepository = AuthRepository.getInstance(sessionManager)
                sessionRefreshManager = SessionRefreshManager(authRepository, sessionManager)
            }
            sessionRefreshManager?.startPeriodicRefresh()
            Log.d(TAG, "SessionRefreshManager started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start SessionRefreshManager", e)
        }
    }

    fun stopSessionRefresh() {
        try {
            sessionRefreshManager?.stop()
            Log.d(TAG, "SessionRefreshManager stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop SessionRefreshManager", e)
        }
    }

    fun startPresence(deviceType: String = "tv") {
        applicationScope.launch {
            try {
                val sessionManager = SessionManager.getInstance(this@TVCallerApplication)
                presenceRepository = PresenceRepository.getInstance(sessionManager)

                val result = presenceRepository?.setOnline(deviceType)
                if (result?.isSuccess == true) {
                    Log.d(TAG, "Presence started successfully")
                } else {
                    Log.e(TAG, "Failed to start presence: ${result?.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start presence", e)
            }
        }
    }

    fun stopPresence() {
        applicationScope.launch {
            try {
                val result = presenceRepository?.setOffline()
                if (result?.isSuccess == true) {
                    Log.d(TAG, "Presence stopped successfully")
                } else {
                    Log.e(TAG, "Failed to stop presence: ${result?.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop presence", e)
            }
        }
    }

    fun getPresenceRepository(): PresenceRepository? {
        return presenceRepository
    }

    fun initializeCallViewModel() {
        try {
            if (callViewModel == null) {
                val sessionManager = SessionManager.getInstance(this)
                callViewModel = CallViewModel(sessionManager)
                callViewModel?.initialize(
                    presenceDataSource = PresenceRepository.getInstance(sessionManager),
                    contactDataSource = ContactRepository.getInstance(sessionManager),
                    callHistoryDataSource = CallHistoryRepository.getInstance(sessionManager),
                    quickDialDataSource = QuickDialRepository.getInstance(sessionManager),
                    webRTCDataSource = WebRTCManager(applicationContext),
                    callNotificationDataSource = CallNotificationManager(applicationContext),
                    signalingService = SignalingManager(sessionManager.getUserId() ?: ""),
                    fcmDataSource = FCMRepository.getInstance(sessionManager)
                )
                Log.d(TAG, "CallViewModel initialized")

                pendingFcmCallData?.let { data ->
                    Log.d(TAG, "Injecting pending FCM call from: ${data.callerName}")
                    callViewModel?.injectFcmIncomingCall(data)
                    pendingFcmCallData = null
                }

                registerFcmToken()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize CallViewModel", e)
        }
    }

    fun updateFcmToken(token: String) {
        applicationScope.launch {
            try {
                val sessionManager = SessionManager.getInstance(this@TVCallerApplication)
                if (!sessionManager.isLoggedIn()) return@launch
                val result = FCMRepository.getInstance(sessionManager).updateToken(token)
                if (result.isSuccess) {
                    Log.d(TAG, "FCM token updated successfully")
                } else {
                    Log.e(TAG, "Failed to update FCM token: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update FCM token", e)
            }
        }
    }

    private fun registerFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM token fetch failed", task.exception)
                return@addOnCompleteListener
            }
            val token = task.result
            Log.d(TAG, "FCM token obtained")
            updateFcmToken(token)
        }
    }

    fun getCallViewModel(): CallViewModel? {
        return callViewModel
    }

    fun invalidateAllCaches() {
        val sessionManager = SessionManager.getInstance(this)
        ContactRepository.getInstance(sessionManager).invalidateCache()
        CallHistoryRepository.getInstance(sessionManager).invalidateCache()
        QuickDialRepository.getInstance(sessionManager).invalidateCache()
        Log.d(TAG, "All caches invalidated")
    }

    fun cleanupCallViewModel() {
        SignalingForegroundService.stopService(this)
        callViewModel = null
        Log.d(TAG, "CallViewModel cleaned up")
    }

    private fun initializeSessionManager() {
        try {
            SessionManager.getInstance(this)
            Log.d(TAG, "SessionManager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SessionManager", e)
            
            throw RuntimeException("Failed to initialize secure storage", e)
        }
    }

    

    private fun initializeSessionRefresh() {
        try {
            val sessionManager = SessionManager.getInstance(this)

            
            if (sessionManager.isLoggedIn()) {
                startSessionRefresh()
                Log.d(TAG, "SessionRefreshManager started (user logged in)")
            } else {
                Log.d(TAG, "SessionRefreshManager not started (no active session)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize SessionRefreshManager", e)
            
            
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        Log.d(TAG, "App moved to foreground")

        val sessionManager = SessionManager.getInstance(this)
        if (sessionManager.isLoggedIn()) {
            if (sessionManager.hasRecentSession(maxAgeHours = 1)) {
                Log.d(TAG, "Session is recent — skipping foreground refresh")
            } else {
                applicationScope.launch {
                    try {
                        val authRepository = AuthRepository.getInstance(sessionManager)
                        authRepository.refreshSession()
                        Log.d(TAG, "Session refreshed on foreground")
                    } catch (e: Exception) {
                        Log.w(TAG, "Session refresh on foreground failed: ${e.message}")
                    }
                }
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d(TAG, "App moved to background - presence stays active via foreground service")
    }

    

    override fun onTerminate() {
        super<Application>.onTerminate()
        Log.d(TAG, "App process terminating")

        
        val sessionManager = SessionManager.getInstance(this)
        if (sessionManager.isLoggedIn()) {
            stopPresence()
        }
    }
}
