package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.calling.repository.PresenceRepository
import com.example.tv_caller_app.datasource.QuickDialDataSource
import com.example.tv_caller_app.model.Contact
import com.example.tv_caller_app.model.QuickDialEntry

class QuickDialRepository private constructor(
    sessionManager: SessionManager
) : QuickDialDataSource {

    private val contactRepository = ContactRepository.getInstance(sessionManager)
    private val callHistoryRepository = CallHistoryRepository.getInstance(sessionManager)
    private val presenceRepository = PresenceRepository.getInstance(sessionManager)

    private var cachedQuickDialEntries: List<QuickDialEntry>? = null
    private var cacheTimestamp: Long = 0

    companion object {
        private const val TAG = "QuickDialRepository"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: QuickDialRepository? = null

        fun getInstance(sessionManager: SessionManager): QuickDialRepository {
            return instance ?: synchronized(this) {
                instance ?: QuickDialRepository(sessionManager).also { instance = it }
            }
        }
    }

    private fun isCacheValid(): Boolean {
        return cachedQuickDialEntries != null &&
               (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    override fun invalidateCache() {
        Log.d(TAG, "Cache invalidated")
        cachedQuickDialEntries = null
        cacheTimestamp = 0
    }

    

    override suspend fun getQuickDialEntries(forceRefresh: Boolean): List<QuickDialEntry> {
        if (!forceRefresh && isCacheValid()) {
            Log.d(TAG, "Returning cached quick dial entries (${cachedQuickDialEntries!!.size} entries)")
            return cachedQuickDialEntries!!
        }

        return try {
            val maxSlots = QuickDialEntry.MAX_QUICK_DIAL_SLOTS
            val allContacts = contactRepository.getAllContacts(forceRefresh = forceRefresh)
            val onlineContactIds = getOnlineContactIds()
            val scoreMap = buildScoreMap()

            Log.d(TAG, "Total contacts: ${allContacts.size}, Online: ${onlineContactIds.size}")

            val onlineContacts = allContacts.filter { it.contactId in onlineContactIds }
            val offlineContacts = allContacts.filter { it.contactId !in onlineContactIds }

            val selected = mutableListOf<Contact>()
            val usedIds = mutableSetOf<Int>()

            
            onlineContacts
                .filter { it.isFavorite }
                .sortedByDescending { scoreMap[it.contactId]?.score ?: 0.0 }
                .forEach {
                    if (selected.size < maxSlots) { selected.add(it); usedIds.add(it.contactId) }
                }
            Log.d(TAG, "After online favorites: ${selected.size}")

            
            onlineContacts
                .filter { !it.isFavorite && it.contactId !in usedIds && scoreMap.containsKey(it.contactId) }
                .sortedByDescending { scoreMap[it.contactId]!!.score }
                .forEach {
                    if (selected.size < maxSlots) { selected.add(it); usedIds.add(it.contactId) }
                }
            Log.d(TAG, "After online scored: ${selected.size}")

            
            onlineContacts
                .filter { it.contactId !in usedIds }
                .shuffled()
                .forEach {
                    if (selected.size < maxSlots) { selected.add(it); usedIds.add(it.contactId) }
                }
            Log.d(TAG, "After online random: ${selected.size}")

            
            if (selected.size < maxSlots) {
                offlineContacts
                    .filter { it.isFavorite && it.contactId !in usedIds }
                    .sortedByDescending { scoreMap[it.contactId]?.score ?: 0.0 }
                    .forEach {
                        if (selected.size < maxSlots) { selected.add(it); usedIds.add(it.contactId) }
                    }
                Log.d(TAG, "After offline favorites: ${selected.size}")
            }

            
            if (selected.size < maxSlots) {
                offlineContacts
                    .filter { !it.isFavorite && it.contactId !in usedIds }
                    .sortedByDescending { scoreMap[it.contactId]?.score ?: 0.0 }
                    .forEach {
                        if (selected.size < maxSlots) { selected.add(it); usedIds.add(it.contactId) }
                    }
                Log.d(TAG, "After offline non-favorites: ${selected.size}")
            }

            val quickDialEntries = selected.mapIndexed { index, contact ->
                val data = scoreMap[contact.contactId]
                QuickDialEntry(
                    contact = contact,
                    position = index + 1,
                    score = data?.score ?: 0.0,
                    callCount = data?.callCount ?: 0
                )
            }

            cachedQuickDialEntries = quickDialEntries
            cacheTimestamp = System.currentTimeMillis()

            quickDialEntries.forEach {
                val online = if (it.contact.contactId in onlineContactIds) "ONLINE" else "offline"
                val fav = if (it.contact.isFavorite) "★" else ""
                Log.d(TAG, "Slot ${it.position}: ${it.contact.username} $fav ($online, score=${it.score}, calls=${it.callCount})")
            }

            quickDialEntries
        } catch (e: Exception) {
            Log.e(TAG, "Error getting quick dial entries: ${e.message}", e)
            cachedQuickDialEntries?.let {
                Log.w(TAG, "Returning stale cached data")
                return it
            }
            emptyList()
        }
    }

    

    private suspend fun buildScoreMap(): Map<Int, ContactData> {
        return try {
            val allCalls = callHistoryRepository.getAllCallHistory()
            val callsWithContacts = allCalls.filter { it.contactId != null }

            if (callsWithContacts.isEmpty()) return emptyMap()

            callsWithContacts
                .groupBy { it.contactId!! }
                .map { (contactId, calls) ->
                    var score = 0.0
                    calls.forEachIndexed { index, call ->
                        val recencyMultiplier = 2.0 - (index * 0.1).coerceAtMost(1.5)
                        val typeWeight = when (call.callType.lowercase()) {
                            "outgoing" -> 1.5
                            "incoming" -> 1.2
                            "missed"   -> 0.5
                            else       -> 1.0
                        }
                        score += recencyMultiplier * typeWeight
                    }
                    ContactData(contactId, score, calls.size)
                }
                .associateBy { it.contactId }
        } catch (e: Exception) {
            Log.w(TAG, "Could not build score map: ${e.message}")
            emptyMap()
        }
    }

    private suspend fun getOnlineContactIds(): Set<Int> {
        return try {
            presenceRepository.getOnlineUsers()
                .getOrNull()
                ?.map { it.contactId }
                ?.toSet() ?: emptySet()
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching online contact IDs", e)
            emptySet()
        }
    }

    private data class ContactData(
        val contactId: Int,
        val score: Double,
        val callCount: Int
    )
}
