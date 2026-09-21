package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.Order
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
     * Locks an order row for a transaction that performs a state transition or payment action.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    fun findByIdForUpdate(@Param("orderId") orderId: UUID): Order?

    /**
     * Order + items in one select (safe when mapping outside an open persistence context).
     * Prefer EntityGraph over JPQL entity name `Order` (SQL reserved word).
     */
    @EntityGraph(attributePaths = ["items"])
    fun findOneById(id: UUID): Order?
}
