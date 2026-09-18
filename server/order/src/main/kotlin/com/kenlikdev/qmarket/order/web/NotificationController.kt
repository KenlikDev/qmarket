package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.common.security.userId
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
    ): PageResponse<NotificationResponse> = notificationService.listMine(authentication.userId(), page, size)

    @GetMapping("/unread-count")
    fun unreadCount(authentication: Authentication): UnreadCountResponse = notificationService.unreadCount(authentication.userId())

    @PostMapping("/{id}/read")
    fun markRead(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): NotificationResponse = notificationService.markRead(authentication.userId(), id)

    @PostMapping("/read-all")
    fun markAllRead(authentication: Authentication): UnreadCountResponse = notificationService.markAllRead(authentication.userId())
}
