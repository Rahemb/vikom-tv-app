package com.example.tv_caller_app.datasource

import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession

interface AuthDataSource {
    suspend fun signUp(email: String, password: String, username: String? = null, phoneNumber: String? = null): Result<String>
    suspend fun signIn(email: String, password: String): Result<UserSession>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentUser(): UserInfo?
    suspend fun refreshSession(): Result<Unit>
    suspend fun isSessionValid(): Boolean
    suspend fun resendVerificationEmail(email: String): Result<Unit>
    suspend fun resetPassword(email: String): Result<Unit>
}
