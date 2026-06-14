package com.example.tv_caller_app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ActiveCall(
    val id: String,

    @SerialName("caller_id")
    val callerId: String,

    @SerialName("callee_id")
    val calleeId: String,

    @SerialName("call_status")
    val callStatus: String,  

    @SerialName("media_type")
    val mediaType: String = "audio",  

    @SerialName("started_at")
    val startedAt: String? = null,

    @SerialName("connected_at")
    val connectedAt: String? = null,

    @SerialName("ended_at")
    val endedAt: String? = null,

    @SerialName("duration_seconds")
    val durationSeconds: Int = 0,

    @SerialName("ice_connection_state")
    val iceConnectionState: String? = null,

    @SerialName("connection_quality")
    val connectionQuality: String? = "unknown",  

    @SerialName("end_reason")
    val endReason: String? = null,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null
)

@Serializable
data class ActiveCallInsert(
    @SerialName("caller_id")
    val callerId: String,

    @SerialName("callee_id")
    val calleeId: String,

    @SerialName("call_status")
    val callStatus: String = "initiating",

    @SerialName("media_type")
    val mediaType: String = "audio"
)

@Serializable
data class ActiveCallUpdate(
    @SerialName("call_status")
    val callStatus: String? = null,

    @SerialName("connected_at")
    val connectedAt: String? = null,

    @SerialName("ended_at")
    val endedAt: String? = null,

    @SerialName("duration_seconds")
    val durationSeconds: Int? = null,

    @SerialName("ice_connection_state")
    val iceConnectionState: String? = null,

    @SerialName("connection_quality")
    val connectionQuality: String? = null,

    @SerialName("end_reason")
    val endReason: String? = null
)
