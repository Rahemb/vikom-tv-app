package com.example.tv_caller_app.datasource

import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.Profile

interface ContactDataSource {
    suspend fun getAllContacts(forceRefresh: Boolean = false): List<Contact>
    suspend fun getContactById(contactId: String): Contact?
    suspend fun getContactByContactId(contactId: Int): Contact?
    suspend fun getFavoriteContacts(forceRefresh: Boolean = false): List<Contact>
    suspend fun createContact(contactId: Int, email: String? = null, address: String? = null, notes: String? = null, isFavorite: Boolean = false): Boolean
    suspend fun updateContact(id: String, contactId: Int, email: String? = null, address: String? = null, notes: String? = null, isFavorite: Boolean = false): Boolean
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean): Boolean
    suspend fun updateNickname(id: String, nickname: String?): Boolean
    suspend fun searchAllUsers(query: String): List<Profile>
    suspend fun deleteContact(contactId: String): Boolean
    fun invalidateCache()
}
