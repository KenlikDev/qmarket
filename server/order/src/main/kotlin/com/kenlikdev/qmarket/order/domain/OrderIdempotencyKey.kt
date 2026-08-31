package com.kenlikdev.qmarket.order.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "order_idempotency_keys",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_order_idempotency_user_key", columnNames = ["user_id", "idem_key"]),
    ],
)
class OrderIdempotencyKey(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,
    @Column(name = "user_id", nullable = false)
    var userId: UUID = UUID(0, 0),
    @Column(name = "idem_key", nullable = false, length = 128)
    var key: String = "",
    @Column(name = "order_id", nullable = false)
    var orderId: UUID = UUID(0, 0),
    /** SHA-256 hex of normalized request; blank for pre-V8 rows. */
    @Column(name = "request_hash", nullable = false, length = 64)
    var requestHash: String = "",
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),
)
