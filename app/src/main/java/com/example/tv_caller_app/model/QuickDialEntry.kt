package com.example.tv_caller_app.model

data class QuickDialEntry(
    val contact: Contact,
    val position: Int,
    val score: Double = 0.0,
    val callCount: Int = 0
) {
    companion object {
        const val MAX_QUICK_DIAL_SLOTS = 4
    }
}
