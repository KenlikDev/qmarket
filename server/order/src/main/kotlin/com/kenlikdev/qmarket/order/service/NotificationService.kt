package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.order.domain.UserNotification
import com.kenlikdev.qmarket.order.dto.NotificationResponse
import com.kenlikdev.qmarket.order.dto.PageResponse
import com.kenlikdev.qmarket.order.dto.UnreadCountResponse
import com.kenlikdev.qmarket.order.repository.UserNotificationRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class NotificationService(
    private val notificationRepository: UserNotificationRepository,
) {
    @Transactional
    fun notifyOrderEvent(
        userId: UUID,
        type: String,
        title: String,
        body: String,
        orderId: UUID?,
    ) {
        notificationRepository.save(
            UserNotification(
                userId = userId,
                type = type,
                title = title.take(200),
                body = body.take(1000),
                relatedOrderId = orderId,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun listMine(
        userId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<NotificationResponse> {
        val pageable = PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, 100))
        val result = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
        return PageResponse(
            content = result.content.map { it.toResponse() },
            page = result.number,
            size = result.size,
            totalElements = result.totalElements,
            totalPages = result.totalPages,
        )
    }

    @Transactional(readOnly = true)
    fun unreadCount(userId: UUID): UnreadCountResponse =
        UnreadCountResponse(unread = notificationRepository.countByUserIdAndReadFalse(userId))

    @Transactional
    fun markRead(
        userId: UUID,
        id: UUID,
    ): NotificationResponse {
        val n =
            notificationRepository.findByIdAndUserId(id, userId)
                ?: throw NotFoundException("Notification not found")
        n.read = true
        return notificationRepository.save(n).toResponse()
    }

    @Transactional
    fun markAllRead(userId: UUID): UnreadCountResponse {
        val page = notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 200))
        page.content.filter { !it.read }.forEach {
            it.read = true
            notificationRepository.save(it)
        }
        return unreadCount(userId)
    }

    private fun UserNotification.toResponse() =
        NotificationResponse(
            id = id!!,
            type = type,
            title = title,
            body = body,
            relatedOrderId = relatedOrderId,
            read = read,
            createdAt = createdAt,
        )
}
