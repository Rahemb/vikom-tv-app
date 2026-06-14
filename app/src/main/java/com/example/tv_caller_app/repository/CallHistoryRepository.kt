package com.example.tv_caller_app.repository

import android.util.Log
import com.example.tv_caller_app.auth.SessionManager
import com.example.tv_caller_app.model.CallHistory
import com.example.tv_caller_app.model.CallHistoryInsert
import com.example.tv_caller_app.datasource.CallHistoryDataSource
import com.example.tv_caller_app.network.SupabaseClient
import io.github.jan.supabase.postgrest.from

class CallHistoryRepository private constructor(
    private val sessionManager: SessionManager
) : CallHistoryDataSource {

    private val supabase = SupabaseClient.client

    private var cachedCallHistory: List<CallHistory>? = null
    private var cacheTimestamp: Long = 0

    companion object {
        private const val TAG = "CallHistoryRepository"
        private const val CACHE_DURATION_MS = 5 * 60 * 1000L

        @Volatile
        private var instance: CallHistoryRepository? = null

        fun getInstance(sessionManager: SessionManager): CallHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: CallHistoryRepository(sessionManager).also { instance = it }
            }
        }
    }

    

    private fun getUserId(): String {
        return sessionManager.getUserId()
            ?: throw IllegalStateException("User not logged in")
    }

    

    private fun isCacheValid(): Boolean {
        return cachedCallHistory != null &&
               (System.currentTimeMillis() - cacheTimestamp) < CACHE_DURATION_MS
    }

    

    override fun invalidateCache() {
        Log.d(TAG, "Cache invalidated")
        cachedCallHistory = null
        cacheTimestamp = 0
    }

    

    override suspend fun getAllCallHistory(forceRefresh: Boolean): List<CallHistory> {
        if (!forceRefresh && isCacheValid()) {
            Log.d(TAG, "Returning cached call history (${cachedCallHistory!!.size} records)")
            return cachedCallHistory!!
        }

        return try {
            val userId = getUserId()
            Log.d(TAG, "Fetching all call history for user: ${userId.take(8)}...")

            val callHistory = supabase.from("call_history")
                .select {
                    filter {
                        eq("user_id", userId)
                    }
                }
                .decodeList<CallHistory>()
                .sortedByDescending { it.callTimestamp }

            cachedCallHistory = callHistory
            cacheTimestamp = System.currentTimeMillis()

            Log.d(TAG, "Successfully fetched and cached ${callHistory.size} call records")
            callHistory
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching call history: ${e.message}", e)
            e.printStackTrace()

            cachedCallHistory?.let {
                Log.w(TAG, "Returning stale cached data due to network error")
                return it
            }

            emptyList()
        }
    }

    

    override suspend fun getRecentCallHistory(limit: Int, forceRefresh: Boolean): List<CallHistory> {
        return try {
            Log.d(TAG, "Getting recent $limit call history records...")
            val allCalls = getAllCallHistory(forceRefresh)
            val recentCalls = allCalls.take(limit)
            Log.d(TAG, "Successfully got ${recentCalls.size} recent call records")
            recentCalls
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching recent call history: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    

    override suspend fun getCallHistoryByType(callType: String, forceRefresh: Boolean): List<CallHistory> {
        return try {
            Log.d(TAG, "Getting call history of type '$callType'...")
            val allCalls = getAllCallHistory(forceRefresh)
            val filteredCalls = allCalls.filter { it.callType.lowercase() == callType.lowercase() }
            Log.d(TAG, "Successfully got ${filteredCalls.size} '$callType' call records")
            filteredCalls
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching call history by type: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    

    override suspend fun getCallHistoryForContact(contactId: Int, forceRefresh: Boolean): List<CallHistory> {
        return try {
            Log.d(TAG, "Getting call history for contact ID $contactId...")
            val allCalls = getAllCallHistory(forceRefresh)
            val contactCalls = allCalls.filter { it.contactId == contactId }
            Log.d(TAG, "Successfully got ${contactCalls.size} call records for contact")
            contactCalls
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching call history for contact: ${e.message}", e)
            e.printStackTrace()
            emptyList()
        }
    }

    override suspend fun logCall(entry: CallHistoryInsert) {
        try {
            Log.d(TAG, "Logging call: type=${entry.callType}, contact=${entry.contactName}, duration=${entry.callDuration}s, reason=${entry.endReason}")
            supabase.from("call_history").insert(entry)
            invalidateCache()
            Log.i(TAG, "Call logged successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log call: ${e.message}", e)
        }
    }

    override suspend fun getCallStatistics(forceRefresh: Boolean): Map<String, Int> {
        return try {
            Log.d(TAG, "Calculating call statistics...")
            val allCalls = getAllCallHistory(forceRefresh)
            val stats = mapOf(
                "total" to allCalls.size,
                "incoming" to allCalls.count { it.callType.lowercase() == "incoming" },
                "outgoing" to allCalls.count { it.callType.lowercase() == "outgoing" },
                "missed" to allCalls.count { it.callType.lowercase() == "missed" }
            )
            Log.d(TAG, "Call statistics: $stats")
            stats
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating call statistics: ${e.message}", e)
            emptyMap()
        }
    }
}
