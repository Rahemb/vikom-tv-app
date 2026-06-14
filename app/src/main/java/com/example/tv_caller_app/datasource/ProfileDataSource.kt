package com.example.tv_caller_app.datasource

import com.example.tv_caller_app.model.Profile

class UsernameNotUniqueException : Exception("Username is already taken")

interface ProfileDataSource {
    suspend fun getProfile(): Profile?
    suspend fun isUsernameTaken(username: String): Boolean
    suspend fun updateUsername(username: String): Boolean
    suspend fun uploadAvatar(imageBytes: ByteArray): String?
    suspend fun setPresetAvatar(avatarName: String): Boolean
    suspend fun deleteAvatar(): Boolean
}
