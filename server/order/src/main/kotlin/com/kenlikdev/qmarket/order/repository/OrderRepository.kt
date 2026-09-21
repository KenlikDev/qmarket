package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.Order
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrderRepository : JpaRepository<Order, UUID> {
    fun findByUserIdOrderByCreatedAtDescIdDesc(
        userId: UUID,
        pageable: Pageable,
    ): Page<Order>

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): Order?

    /**
     * Order + items in one select (safe when mapping outside an open persistence context).
     * Prefer EntityGraph over JPQL entity name `Order` (SQL reserved word).
     */
    @EntityGraph(attributePaths = ["items"])
    fun findOneById(id: UUID): Order?
}
