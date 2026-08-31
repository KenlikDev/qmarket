package com.kenlikdev.qmarket.order.dto

import java.time.Instant
import java.util.UUID

data class NotificationResponse(
    val id: UUID,
    val type: String,
    val title: String,
    val body: String,
    val relatedOrderId: UUID?,
    val read: Boolean,
    val createdAt: Instant,
)

data class UnreadCountResponse(
    val unread: Long,
)
