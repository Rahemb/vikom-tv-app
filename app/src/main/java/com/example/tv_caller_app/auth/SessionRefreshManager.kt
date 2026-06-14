package com.example.tv_caller_app.auth

import android.util.Log
import com.example.tv_caller_app.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SessionRefreshManager(
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) {

    companion object {
        private const val TAG = "SessionRefreshManager"
        private const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    

    fun startPeriodicRefresh() {
        if (isRunning) {
            Log.d(TAG, "Periodic refresh already running")
            return
        }

        isRunning = true
        Log.d(TAG, "Starting periodic session refresh")

        scope.launch {
            while (isRunning) {
                delay(REFRESH_INTERVAL_MS)

                if (sessionManager.isLoggedIn()) {
                    Log.d(TAG, "Attempting session refresh")
                    authRepository.refreshSession()
                        .onSuccess {
                            Log.d(TAG, "Session refreshed successfully")
                        }
                        .onFailure { error ->
                            Log.w(TAG, "Session refresh failed: ${error.message}")
                        }
                } else {
                    Log.d(TAG, "User not logged in, skipping refresh")
                }
            }
        }
    }

    

    fun stop() {
        Log.d(TAG, "Stopping periodic session refresh")
        isRunning = false
        scope.cancel()
    }
}
