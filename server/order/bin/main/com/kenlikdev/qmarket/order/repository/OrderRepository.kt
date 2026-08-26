package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.Order
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(
        userId: UUID,
        pageable: Pageable,
    ): Page<Order>

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Order?
}
