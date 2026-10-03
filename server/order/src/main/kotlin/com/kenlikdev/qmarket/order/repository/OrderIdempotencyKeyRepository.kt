package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.OrderIdempotencyKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface OrderIdempotencyKeyRepository : JpaRepository<OrderIdempotencyKey, UUID> {
    fun findByUserIdAndKey(
        userId: UUID,
        key: String,
    ): OrderIdempotencyKey?

    @Modifying
    @Query(
        value = """
            DELETE FROM order_idempotency_keys
            WHERE id IN (
                SELECT id
                FROM order_idempotency_keys
                WHERE created_at < :cutoff
                ORDER BY created_at ASC, id ASC
                LIMIT :batchSize
            )
            """,
        nativeQuery = true,
    )
    fun deleteCreatedBefore(
        @Param("cutoff") cutoff: java.time.Instant,
        @Param("batchSize") batchSize: Int,
    ): Int
}
