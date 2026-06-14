package com.example.tv_caller_app.datasource

import com.example.tv_caller_app.model.QuickDialEntry

interface QuickDialDataSource {
    suspend fun getQuickDialEntries(forceRefresh: Boolean = false): List<QuickDialEntry>
    fun invalidateCache()
}
