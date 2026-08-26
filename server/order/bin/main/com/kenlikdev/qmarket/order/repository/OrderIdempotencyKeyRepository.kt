package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.OrderIdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderIdempotencyKeyRepository : JpaRepository<OrderIdempotencyKey, UUID> {
    fun findByUserIdAndKey(
        userId: UUID,
        key: String,
    ): OrderIdempotencyKey?
}
