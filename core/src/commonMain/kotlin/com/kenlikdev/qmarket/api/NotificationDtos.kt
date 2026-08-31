package com.kenlikdev.qmarket.api

import kotlinx.serialization.Serializable

@Serializable
data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val relatedOrderId: String? = null,
    val read: Boolean = false,
    val createdAt: String? = null,
)

@Serializable
data class UnreadCountDto(
    val unread: Long,
)
