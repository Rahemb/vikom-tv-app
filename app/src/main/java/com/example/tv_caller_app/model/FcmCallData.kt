package com.example.tv_caller_app.model

data class FcmCallData(
    val callerUserId: String,
    val callerName: String,
    val callerUsername: String,
    val callerContactId: String,
    val offerSdp: String
)
