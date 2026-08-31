package com.kenlikdev.qmarket.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "user_notifications")
class UserNotification(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID(0, 0),
    @Column(nullable = false, length = 64)
    var type: String = "",
    @Column(nullable = false, length = 200)
    var title: String = "",
    @Column(nullable = false, length = 1000)
    var body: String = "",
    @Column(name = "related_order_id")
    var relatedOrderId: UUID? = null,
    @Column(name = "is_read", nullable = false)
    var read: Boolean = false,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
