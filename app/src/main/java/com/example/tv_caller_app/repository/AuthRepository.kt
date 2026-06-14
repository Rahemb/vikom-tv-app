package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.datasource.AuthDataSource
import com.example.tv_caller_app.model.ProfileInsert
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

class AuthRepository private constructor(
    private val sessionManager: SessionManager
) : AuthDataSource {

    private val supabase = SupabaseClient.client

    companion object {
        private const val TAG = "AuthRepository"

        @Volatile
        private var instance: AuthRepository? = null

        fun getInstance(sessionManager: SessionManager): AuthRepository {
            return instance ?: synchronized(this) {
                instance ?: AuthRepository(sessionManager).also { instance = it }
            }
        }
    }

    

    override suspend fun signUp(
        email: String,
        password: String,
        username: String?,
        phoneNumber: String?
    ): Result<String> {
        return retryOnNetworkError {
            try {
                Log.d(TAG, "Attempting sign up for email: $email, username: $username, phoneNumber: $phoneNumber")

                
                val authResponse = supabase.auth.signUpWith(Email) {
                    this.email = email
                    this.password = password

                    
                    if (username != null || phoneNumber != null) {
                        data = buildJsonObject {
                            if (username != null) put("username", username)
                            if (phoneNumber != null) put("phone_number", phoneNumber)
                        }
                    }
                }

                val userId = authResponse?.id
                if (userId != null) {
                    Log.d(TAG, "User created with ID: $userId, username stored in metadata")
                    
                }

                Log.d(TAG, "Sign up successful, verification email sent to: $email")
                Result.success("Verification email sent to $email")
            } catch (e: Exception) {
                Log.e(TAG, "Sign up failed: ${e.message}", e)
                Result.failure(mapAuthException(e))
            }
        }
    }

    

    private suspend fun createProfile(
        userId: String,
        email: String,
        username: String? = null
    ) {
        try {
            
            val finalUsername = username ?: email.substringBefore("@")

            val profileInsert = ProfileInsert(
                id = userId,
                username = finalUsername,
                email = email
            )

            supabase.from("profiles").insert(profileInsert)

            Log.d(TAG, "Profile created successfully for user: $userId (username: $finalUsername, email: $email)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create profile: ${e.message}", e)
            throw e
        }
    }

    

    override suspend fun signIn(email: String, password: String): Result<UserSession> {
        return retryOnNetworkError {
            try {
                Log.d(TAG, "Attempting sign in for email: $email")

                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                
                val session = supabase.auth.currentSessionOrNull()
                    ?: throw Exception("Authentication failed - no session created")

                
                val user = supabase.auth.currentUserOrNull()
                    ?: throw Exception("Authentication failed - no user info")

                
                if (user.confirmedAt == null) {
                    
                    supabase.auth.signOut()
                    throw Exception("Please verify your email before signing in. Check your inbox for the verification link.")
                }

                val userId = session.user?.id ?: throw Exception("No user ID in session")

                
                try {
                    ensureProfileExists(userId, user.email ?: email)
                } catch (profileError: Exception) {
                    Log.w(TAG, "Profile creation/check failed: ${profileError.message}")
                    
                }

                
                sessionManager.saveSession(
                    userId = userId,
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    email = user.email ?: email,
                    emailConfirmed = true
                )

                Log.d(TAG, "Sign in successful for user: $userId")
                Result.success(session)
            } catch (e: Exception) {
                Log.e(TAG, "Sign in failed: ${e.message}", e)
                Result.failure(mapAuthException(e))
            }
        }
    }

    

    private suspend fun ensureProfileExists(userId: String, email: String) {
        try {
            
            val existingProfile = supabase.from("profiles")
                .select {
                    filter {
                        eq("id", userId)
                    }
                }
                .decodeSingleOrNull<ProfileInsert>()

            if (existingProfile == null) {
                
                Log.d(TAG, "Profile not found for user $userId, creating...")

                
                val user = supabase.auth.currentUserOrNull()

                
                Log.d(TAG, "User metadata: ${user?.userMetadata}")
                Log.d(TAG, "Raw metadata keys: ${user?.userMetadata?.keys}")

                
                val username = try {
                    user?.userMetadata?.get("username")?.jsonPrimitive?.contentOrNull
                } catch (e: Exception) {
                    Log.w(TAG, "Error extracting username from metadata: ${e.message}")
                    null
                }

                Log.d(TAG, "Retrieved username from metadata: '$username'")

                if (username == null) {
                    Log.w(TAG, "username is null! Using username from email as fallback")
                }

                createProfile(userId, email, username)
            } else {
                Log.d(TAG, "Profile already exists for user $userId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ensuring profile exists: ${e.message}", e)
            throw e
        }
    }

    

    override suspend fun signOut(): Result<Unit> {
        return try {
            val userId = sessionManager.getUserId()
            Log.d(TAG, "Signing out user: $userId")

            
            supabase.auth.signOut()

            
            sessionManager.clearSession()

            Log.d(TAG, "Sign out successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sign out failed: ${e.message}", e)
            
            sessionManager.clearSession()
            Result.failure(e)
        }
    }

    

    override suspend fun getCurrentUser(): UserInfo? {
        return try {
            supabase.auth.currentUserOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current user: ${e.message}", e)
            null
        }
    }

    

    override suspend fun refreshSession(): Result<Unit> {
        return try {
            Log.d(TAG, "Refreshing session...")

            
            
            if (supabase.auth.currentSessionOrNull() == null) {
                val accessToken = sessionManager.getAccessToken()
                val refreshToken = sessionManager.getRefreshToken()
                if (accessToken != null && refreshToken != null) {
                    Log.d(TAG, "No in-memory session — importing tokens from SessionManager")
                    supabase.auth.importSession(
                        UserSession(
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            tokenType = "Bearer",
                            expiresIn = 3600,
                            user = null
                        )
                    )
                } else {
                    Log.w(TAG, "No tokens in SessionManager — cannot restore session")
                }
            }

            supabase.auth.refreshCurrentSession()

            
            val session = supabase.auth.currentSessionOrNull()
                ?: throw Exception("No session found after refresh")

            val user = supabase.auth.currentUserOrNull()
                ?: throw Exception("No user found after refresh")

            
            sessionManager.saveSession(
                userId = user.id,
                accessToken = session.accessToken,
                refreshToken = session.refreshToken,
                email = user.email ?: sessionManager.getEmail() ?: "",
                emailConfirmed = user.confirmedAt != null
            )

            Log.d(TAG, "Session refreshed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Session refresh failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    

    override suspend fun resendVerificationEmail(email: String): Result<Unit> {
        return retryOnNetworkError {
            try {
                Log.d(TAG, "Resending verification email to: $email")

                supabase.auth.resendEmail(
                    type = OtpType.Email.SIGNUP,
                    email = email
                )

                Log.d(TAG, "Verification email resent successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resend verification email: ${e.message}", e)
                Result.failure(mapAuthException(e))
            }
        }
    }

    

    override suspend fun resetPassword(email: String): Result<Unit> {
        return retryOnNetworkError {
            try {
                Log.d(TAG, "Requesting password reset for: $email")

                supabase.auth.resetPasswordForEmail(email)

                Log.d(TAG, "Password reset email sent successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Password reset failed: ${e.message}", e)
                Result.failure(mapAuthException(e))
            }
        }
    }

    

    override suspend fun isSessionValid(): Boolean {
        return try {
            
            val isLoggedIn = sessionManager.isLoggedIn()

            if (!isLoggedIn) {
                Log.d(TAG, "No stored session found")
                return false
            }

            
            
            Log.d(TAG, "Valid stored session found")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error checking session validity: ${e.message}", e)
            false
        }
    }

    

    private fun mapAuthException(e: Exception): Exception {
        val message = when {
            
            e.message?.contains("Invalid login credentials", ignoreCase = true) == true ->
                "Invalid email or password"
            e.message?.contains("Email not confirmed", ignoreCase = true) == true ->
                "Please verify your email before signing in. Check your inbox."
            e.message?.contains("User already registered", ignoreCase = true) == true ->
                "An account with this email already exists. Try logging in instead."
            e.message?.contains("User not found", ignoreCase = true) == true ->
                "No account found with this email"
            e.message?.contains("Invalid email", ignoreCase = true) == true ->
                "Please enter a valid email address"

            
            e.message?.contains("Email link is invalid", ignoreCase = true) == true ->
                "Verification link is invalid or expired. Request a new one."
            e.message?.contains("OTP expired", ignoreCase = true) == true ->
                "Verification code expired. Request a new one."

            
            e.message?.contains("Password", ignoreCase = true) == true &&
            e.message?.contains("weak", ignoreCase = true) == true ->
                "Password is too weak. Use at least 8 characters with uppercase, lowercase, number, and special character."
            e.message?.contains("Password should be at least", ignoreCase = true) == true ->
                "Password must be at least 8 characters long"

            
            e.message?.contains("network", ignoreCase = true) == true ||
            e.message?.contains("timeout", ignoreCase = true) == true ->
                "Network error. Please check your connection and try again."
            e.message?.contains("Unable to resolve host", ignoreCase = true) == true ->
                "Cannot reach server. Check your internet connection."

            
            e.message?.contains("refresh_token_not_found", ignoreCase = true) == true ->
                "Session expired. Please log in again."
            e.message?.contains("invalid_grant", ignoreCase = true) == true ->
                "Session expired. Please log in again."
            e.message?.contains("JWT", ignoreCase = true) == true ||
            e.message?.contains("token", ignoreCase = true) == true ->
                "Session expired. Please log in again."

            
            e.message?.contains("rate limit", ignoreCase = true) == true ||
            e.message?.contains("too many requests", ignoreCase = true) == true ->
                "Too many attempts. Please wait a moment and try again."

            
            e.message?.contains("already been taken", ignoreCase = true) == true ->
                "This email is already registered. Try logging in instead."

            
            else -> {
                Log.w(TAG, "Unmapped auth error: ${e.message}")
                "Authentication error. Please try again."
            }
        }

        return Exception(message)
    }

    

    private suspend fun <T> retryOnNetworkError(
        maxRetries: Int = 3,
        initialDelay: Long = 1000L,
        maxDelay: Long = 5000L,
        factor: Double = 2.0,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay

        repeat(maxRetries) { attempt ->
            val result = block()

            if (result.isSuccess) {
                return result
            }

            val error = result.exceptionOrNull()
            val isNetworkError = error?.message?.contains("network", ignoreCase = true) == true ||
                    error?.message?.contains("timeout", ignoreCase = true) == true ||
                    error?.message?.contains("Unable to resolve host", ignoreCase = true) == true

            if (isNetworkError) {
                if (attempt < maxRetries - 1) {
                    Log.d(TAG, "Network error on attempt ${attempt + 1}, retrying after ${currentDelay}ms")
                    kotlinx.coroutines.delay(currentDelay)
                    currentDelay = (currentDelay * factor).toLong().coerceAtMost(maxDelay)
                } else {
                    Log.w(TAG, "Network error after $maxRetries attempts, giving up")
                }
            } else {
                
                Log.d(TAG, "Non-network error, not retrying: ${error?.message}")
                return result
            }
        }

        return Result.failure(Exception("Network error after $maxRetries attempts. Please check your connection."))
    }
}
