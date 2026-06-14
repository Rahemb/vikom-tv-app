package com.example.tv_caller_app.datasource

import com.example.tv_caller_app.model.CallHistory
import com.example.tv_caller_app.model.CallHistoryInsert

interface CallHistoryDataSource {
    suspend fun getAllCallHistory(forceRefresh: Boolean = false): List<CallHistory>
    suspend fun getRecentCallHistory(limit: Int = 10, forceRefresh: Boolean = false): List<CallHistory>
    suspend fun getCallHistoryByType(callType: String, forceRefresh: Boolean = false): List<CallHistory>
    suspend fun getCallHistoryForContact(contactId: Int, forceRefresh: Boolean = false): List<CallHistory>
    suspend fun logCall(entry: CallHistoryInsert)
    suspend fun getCallStatistics(forceRefresh: Boolean = false): Map<String, Int>
    fun invalidateCache()
}
