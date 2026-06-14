package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.ContactInsert
import com.example.tv_caller_app.model.ContactUpdate
import com.example.tv_caller_app.model.Profile
import com.example.tv_caller_app.datasource.ContactDataSource
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ContactRepository private constructor(
    private val sessionManager: SessionManager
) : ContactDataSource {

    private val supabase = SupabaseClient.client

    private var cachedContacts: List<Contact>? = null
    private var cacheTimestamp: Long = 0

    companion object {
        private const val TAG = "ContactRepository"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: ContactRepository? = null

        fun getInstance(sessionManager: SessionManager): ContactRepository {
            return instance ?: synchronized(this) {
                instance ?: ContactRepository(sessionManager).also { instance = it }
            }
        }
    }

    

    private fun getUserId(): String {
        return sessionManager.getUserId()
            ?: throw IllegalStateException("User not logged in")
    }

    

    private fun isCacheValid(): Boolean {
        return cachedContacts != null &&
               (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    

    override fun invalidateCache() {
        Log.d(TAG, "Cache invalidated")
        cachedContacts = null
        cacheTimestamp = 0
    }

    

    override suspend fun getAllContacts(forceRefresh: Boolean): List<Contact> {
        if (!forceRefresh && isCacheValid()) {
            Log.d(TAG, "Returning cached contacts (${cachedContacts!!.size} contacts)")
            return cachedContacts!!
        }

        return try {
            val userId = getUserId()
            Log.d(TAG, "Fetching all contacts for user: ${userId.take(8)}...")
            @Serializable
            data class ContactRow(
                val id: String,
                @SerialName("user_id") val userId: String,
                @SerialName("contact_id") val contactId: Int,
                val email: String? = null,
                val address: String? = null,
                val notes: String? = null,
                val nickname: String? = null,
                @SerialName("is_favorite") val isFavorite: Boolean = false,
                @SerialName("created_at") val createdAt: String? = null,
                @SerialName("updated_at") val updatedAt: String? = null
            )

            val contactRows = supabase.from("contacts")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<ContactRow>()

            val contactIds = contactRows.map { it.contactId }

            @Serializable
            data class ProfileRow(
                @SerialName("contact_id") val contactId: Int,
                val username: String?,
                @SerialName("avatar_url") val avatarUrl: String? = null
            )

            val profiles = if (contactIds.isNotEmpty()) {
                supabase.from("profiles")
                    .select {
                        filter {
                            isIn("contact_id", contactIds)
                        }
                    }
                    .decodeList<ProfileRow>()
            } else {
                emptyList()
            }

            val profileMap = profiles.associateBy { it.contactId }

            val contacts = contactRows.map { row ->
                val profile = profileMap[row.contactId]
                Contact(
                    id = row.id,
                    userId = row.userId,
                    contactId = row.contactId,
                    username = profile?.username ?: "Unknown",
                    email = row.email,
                    address = row.address,
                    notes = row.notes,
                    nickname = row.nickname,
                    isFavorite = row.isFavorite,
                    avatarUrl = profile?.avatarUrl,
                    createdAt = row.createdAt,
                    updatedAt = row.updatedAt
                )
            }

            cachedContacts = contacts
            cacheTimestamp = System.currentTimeMillis()

            Log.d(TAG, "Successfully fetched and cached ${contacts.size} contacts")
            contacts
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all contacts: ${e.message}", e)
            e.printStackTrace()

            cachedContacts?.let {
                Log.w(TAG, "Returning stale cached data due to network error")
                return it
            }

            emptyList()
        }
    }

    

    override suspend fun getContactById(contactId: String): Contact? {
        return try {
            Log.d(TAG, "Getting contact by ID: $contactId")

            cachedContacts?.let { contacts ->
                val cachedContact = contacts.find { it.id == contactId }
                if (cachedContact != null && isCacheValid()) {
                    Log.d(TAG, "Returning cached contact: ${cachedContact.username}")
                    return cachedContact
                }
            }

            Log.d(TAG, "Fetching contact from Supabase...")
            val contacts = supabase.from("contacts")
                .select {
                    filter {
                        eq("id", contactId)
                    }
                }
                .decodeList<Contact>()

            val contact = contacts.firstOrNull()

            if (contact != null) {
                Log.d(TAG, "Successfully fetched contact: ${contact.username}")
            } else {
                Log.w(TAG, "Contact not found with ID: $contactId")
            }

            contact
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching contact by ID: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    

    override suspend fun getContactByContactId(contactId: Int): Contact? {
        return try {
            Log.d(TAG, "Looking up contact by contact ID: $contactId")

            cachedContacts?.let { contacts ->
                val cachedContact = contacts.find { it.contactId == contactId }
                if (cachedContact != null && isCacheValid()) {
                    Log.d(TAG, "Found contact in cache: ${cachedContact.username}")
                    return cachedContact
                }
            }

            val contacts = getAllContacts()
            val contact = contacts.find { it.contactId == contactId }

            if (contact != null) {
                Log.d(TAG, "Found contact: ${contact.username}")
            } else {
                Log.d(TAG, "Contact not found with contact ID: $contactId")
            }

            contact
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact by contact ID: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    

    override suspend fun getFavoriteContacts(forceRefresh: Boolean): List<Contact> {
        return try {
            Log.d(TAG, "Getting favorite contacts...")

            val contacts = getAllContacts(forceRefresh)
            val favorites = contacts.filter { it.isFavorite }

            Log.d(TAG, "Successfully got ${favorites.size} favorite contacts")
            favorites
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching favorite contacts: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    

    override suspend fun createContact(
        contactId: Int,
        email: String?,
        address: String?,
        notes: String?,
        isFavorite: Boolean
    ): Boolean {
        return try {
            val userId = getUserId()
            Log.d(TAG, "Creating new contact with contact ID: $contactId for user: ${userId.take(8)}...")

            val newContact = ContactInsert(
                userId = userId,
                contactId = contactId,
                email = email,
                address = address,
                notes = notes,
                isFavorite = isFavorite
            )

            Log.d(TAG, "Insert data: $newContact")

            supabase.from("contacts").insert(newContact)

            Log.d(TAG, "Successfully created contact")

            invalidateCache()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating contact: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    

    override suspend fun updateContact(
        id: String,
        contactId: Int,
        email: String?,
        address: String?,
        notes: String?,
        isFavorite: Boolean
    ): Boolean {
        return try {
            Log.d(TAG, "Updating contact: $id")

            val updates = ContactUpdate(
                contactId = contactId,
                email = email,
                address = address,
                notes = notes,
                isFavorite = isFavorite
            )

            Log.d(TAG, "Update data: $updates")

            supabase.from("contacts")
                .update(updates) {
                    filter {
                        eq("id", id)
                    }
                }

            Log.d(TAG, "Successfully updated contact: $id")

            invalidateCache()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating contact: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }

    

    override suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean): Boolean {
        return try {
            Log.d(TAG, "Updating favorite status for $id to $isFavorite")

            val update = buildJsonObject {
                put("is_favorite", isFavorite)
            }

            supabase.from("contacts")
                .update(update) {
                    filter {
                        eq("id", id)
                    }
                }

            invalidateCache()
            Log.d(TAG, "Favorite status updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating favorite status: ${e.message}", e)
            false
        }
    }

    override suspend fun updateNickname(id: String, nickname: String?): Boolean {
        return try {
            Log.d(TAG, "Updating nickname for $id to '$nickname'")

            val update = buildJsonObject {
                put("nickname", nickname)
            }

            supabase.from("contacts")
                .update(update) {
                    filter {
                        eq("id", id)
                    }
                }

            invalidateCache()
            Log.d(TAG, "Nickname updated successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating nickname: ${e.message}", e)
            false
        }
    }

    

    override suspend fun searchAllUsers(query: String): List<Profile> {
        return try {
            val userId = getUserId()
            Log.d(TAG, "Searching all users with query: $query")

            val trimmedQuery = query.trim()
            if (trimmedQuery.isEmpty()) return emptyList()

            val numericQuery = trimmedQuery.toLongOrNull()

            val results = supabase.from("profiles")
                .select {
                    filter {
                        neq("id", userId)
                        if (numericQuery != null) {
                            or {
                                ilike("username", "%$trimmedQuery%")
                                eq("contact_id", numericQuery)
                            }
                        } else {
                            ilike("username", "%$trimmedQuery%")
                        }
                    }
                    limit(20)
                }
                .decodeList<Profile>()

            Log.d(TAG, "Found ${results.size} users matching '$query'")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Error searching users: ${e.message}", e)
            emptyList()
        }
    }

    

    override suspend fun deleteContact(contactId: String): Boolean {
        return try {
            Log.d(TAG, "Deleting contact: $contactId")

            supabase.from("contacts")
                .delete {
                    filter {
                        eq("id", contactId)
                    }
                }

            Log.d(TAG, "Successfully deleted contact: $contactId")

            invalidateCache()

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting contact: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
}
