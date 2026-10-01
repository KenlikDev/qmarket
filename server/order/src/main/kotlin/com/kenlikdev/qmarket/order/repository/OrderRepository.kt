package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId and o.userId = :userId")
    fun findByIdAndUserIdForUpdate(
        @Param("orderId") id: UUID,
        @Param("userId") userId: UUID,
    ): Order?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    fun findByIdForUpdate(
        @Param("orderId") id: UUID,
    ): Order?

    @Query(
        """
        select o.id
        from Order o
        where o.status in :statuses
          and (
            o.paymentExpiresAt <= :now
            or (
                o.paymentOperationState in (
                    com.kenlikdev.qmarket.order.domain.PaymentOperationState.CANCELLING,
                    com.kenlikdev.qmarket.order.domain.PaymentOperationState.CREATING
                )
                and o.paymentOperationStartedAt <= :staleBefore
            )
          )
        order by o.paymentExpiresAt asc, o.id asc
        """,
    )
    fun findOrdersRequiringPaymentRecovery(
        @Param("statuses") statuses: Set<OrderStatus>,
        @Param("now") now: Instant,
        @Param("staleBefore") staleBefore: Instant,
        pageable: Pageable,
    ): List<UUID>

    /**
     * Order + items in one select (safe when mapping outside an open persistence context).
     * Prefer EntityGraph over JPQL entity name `Order` (SQL reserved word).
     */
    @EntityGraph(attributePaths = ["items"])
    fun findOneById(id: UUID): Order?
}
