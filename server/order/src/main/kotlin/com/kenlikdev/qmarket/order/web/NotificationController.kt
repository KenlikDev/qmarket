package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.order.dto.NotificationResponse
import com.kenlikdev.qmarket.order.dto.PageResponse
import com.kenlikdev.qmarket.order.dto.UnreadCountResponse
import com.kenlikdev.qmarket.order.service.NotificationService
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController(
    private val notificationService: NotificationService,
) {
    @GetMapping
    fun list(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): PageResponse<NotificationResponse> = notificationService.listMine(currentUserId(authentication), page, size)

    @GetMapping("/unread-count")
    fun unreadCount(authentication: Authentication): UnreadCountResponse = notificationService.unreadCount(currentUserId(authentication))

    @PostMapping("/{id}/read")
    fun markRead(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): NotificationResponse = notificationService.markRead(currentUserId(authentication), id)

    @PostMapping("/read-all")
    fun markAllRead(authentication: Authentication): UnreadCountResponse = notificationService.markAllRead(currentUserId(authentication))

    private fun currentUserId(authentication: Authentication): UUID = authentication.principal as UUID
}
