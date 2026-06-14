package com.example.tv_caller_app.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.tv_caller_app.TVCallerApplication
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.datasource.AuthDataSource
import kotlinx.coroutines.launch

class AuthViewModel(
    application: Application,
    private val authDataSource: AuthDataSource
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    
    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    
    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    
    private val _authSuccess = MutableLiveData(false)
    val authSuccess: LiveData<Boolean> = _authSuccess

    
    private val _loggedOut = MutableLiveData(false)
    val loggedOut: LiveData<Boolean> = _loggedOut

    
    private val _sessionValid = MutableLiveData<Boolean?>(null)
    val sessionValid: LiveData<Boolean?> = _sessionValid

    
    private val _cachedUsername = MutableLiveData<String?>()
    val cachedUsername: LiveData<String?> = _cachedUsername

    
    private val _emailVerificationRequired = MutableLiveData<String?>() 
    val emailVerificationRequired: LiveData<String?> = _emailVerificationRequired

    
    private val _emailError = MutableLiveData<String?>()
    val emailError: LiveData<String?> = _emailError

    private val _passwordError = MutableLiveData<String?>()
    val passwordError: LiveData<String?> = _passwordError

    fun checkSession() {
        viewModelScope.launch {
            try {
                val isValid = authDataSource.isSessionValid()
                if (isValid) {
                    val sm = SessionManager.getInstance(getApplication())
                    sm.updateEmailConfirmed(true)
                    _cachedUsername.value = sm.getUsername()
                }
                _sessionValid.value = isValid
            } catch (e: Exception) {
                Log.e(TAG, "Error checking session", e)
                _sessionValid.value = false
            }
        }
    }

    

    fun login(email: String, password: String) {
        Log.d(TAG, "login() called with email: $email")

        
        _emailError.value = null
        _passwordError.value = null
        _errorMessage.value = null
        _emailVerificationRequired.value = null

        
        if (!validateEmail(email)) {
            _emailError.value = "Please enter a valid email"
            return
        }

        if (password.isBlank()) {
            _passwordError.value = "Please enter your password"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authDataSource.signIn(email, password)

                if (result.isSuccess) {
                    Log.d(TAG, "Login successful")
                    _authSuccess.value = true
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Login failed"
                    Log.e(TAG, "Login failed: $error")
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Login error", e)
                _errorMessage.value = e.message ?: "Login failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    

    fun register(email: String, password: String, confirmPassword: String, username: String? = null, phoneNumber: String? = null) {
        Log.d(TAG, "register() called with email: $email, username: $username, phoneNumber: $phoneNumber")

        
        _emailError.value = null
        _passwordError.value = null
        _errorMessage.value = null
        _emailVerificationRequired.value = null

        
        if (!validateEmail(email)) {
            _emailError.value = "Please enter a valid email"
            return
        }

        if (!validatePassword(password)) {
            _passwordError.value = getPasswordRequirements()
            return
        }

        if (password != confirmPassword) {
            _passwordError.value = "Passwords do not match"
            return
        }

        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authDataSource.signUp(email, password, username, phoneNumber)

                if (result.isSuccess) {
                    Log.d(TAG, "Registration successful - verification email sent")
                    _emailVerificationRequired.value = email
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Registration failed"
                    Log.e(TAG, "Registration failed: $error")
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Registration error", e)
                _errorMessage.value = e.message ?: "Registration failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    

    private fun validateEmail(email: String): Boolean {
        if (email.isBlank()) return false
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    

    private fun validatePassword(password: String): Boolean {
        if (password.length < 8) return false

        val hasUpperCase = password.any { it.isUpperCase() }
        val hasLowerCase = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }

        return hasUpperCase && hasLowerCase && hasDigit && hasSpecial
    }

    

    private fun getPasswordRequirements(): String {
        return "Password must contain:\n" +
                "• At least 8 characters\n" +
                "• One uppercase letter\n" +
                "• One lowercase letter\n" +
                "• One number\n" +
                "• One special character"
    }

    

    fun clearError() {
        _errorMessage.value = null
    }

    

    fun clearEmailError() {
        _emailError.value = null
    }

    

    fun clearPasswordError() {
        _passwordError.value = null
    }

    

    fun resetAuthSuccess() {
        _authSuccess.value = false
    }

    

    fun resendVerificationEmail(email: String) {
        Log.d(TAG, "Resending verification email to: $email")
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val result = authDataSource.resendVerificationEmail(email)

                if (result.isSuccess) {
                    Log.d(TAG, "Verification email resent successfully")
                    _errorMessage.value = "Verification email sent! Check your inbox."
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to send email"
                    Log.e(TAG, "Failed to resend verification email: $error")
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resending verification email", e)
                _errorMessage.value = e.message ?: "Failed to send email"
            } finally {
                _isLoading.value = false
            }
        }
    }

    

    fun resetEmailVerification() {
        _emailVerificationRequired.value = null
    }

    

    fun logout() {
        Log.d(TAG, "Logout requested")
        _isLoading.value = true

        val app = getApplication<TVCallerApplication>()
        app.invalidateAllCaches()
        app.stopSessionRefresh()
        app.stopPresence()
        app.cleanupCallViewModel()

        viewModelScope.launch {
            try {
                val result = authDataSource.signOut()

                if (result.isSuccess) {
                    Log.d(TAG, "Logout successful")
                    _loggedOut.value = true
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Logout failed"
                    Log.e(TAG, "Logout failed: $error")
                    _errorMessage.value = error
                }
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
                _errorMessage.value = e.message ?: "Logout failed"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
