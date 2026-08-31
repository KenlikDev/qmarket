package com.kenlikdev.qmarket.order.repository

import com.kenlikdev.qmarket.order.domain.UserNotification
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface UserNotificationRepository : JpaRepository<UserNotification, UUID> {
    fun findByUserIdOrderByCreatedAtDesc(
        userId: UUID,
        pageable: Pageable,
    ): Page<UserNotification>

    fun countByUserIdAndReadFalse(userId: UUID): Long

    fun findByIdAndUserId(
        id: UUID,
        userId: UUID,
    ): UserNotification?
}
