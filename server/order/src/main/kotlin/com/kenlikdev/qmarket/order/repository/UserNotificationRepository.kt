package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.UserNotification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserNotificationRepository : JpaRepository<UserNotification, UUID> {
    fun findByUserIdOrderByCreatedAtDescIdDesc(
        userId: UUID,
        pageable: Pageable,
    ): Page<UserNotification>

    fun countByUserIdAndReadFalse(userId: UUID): Long

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): UserNotification?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        value = """
            UPDATE user_notifications
            SET is_read = TRUE
            WHERE user_id = :userId
              AND is_read = FALSE
            """,
        nativeQuery = true,
    )
    fun markAllReadForUser(
        @Param("userId") userId: UUID,
    ): Int
}
