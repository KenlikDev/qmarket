package com.kenlikdev.qmarket.order.web

import com.kenlikdev.qmarket.common.exception.GlobalExceptionHandler
import com.kenlikdev.qmarket.order.dto.NotificationResponse
import com.kenlikdev.qmarket.order.dto.PageResponse
import com.kenlikdev.qmarket.order.dto.UnreadCountResponse
import com.kenlikdev.qmarket.order.service.NotificationService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant
import java.util.UUID

class NotificationControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var notificationService: NotificationService
    private val userId = UUID.randomUUID()
    private val auth = UsernamePasswordAuthenticationToken(userId, null, emptyList())

    private val sample =
        NotificationResponse(
            id = UUID.randomUUID(),
            type = "ORDER_PAID",
            title = "Paid",
            body = "ok",
            relatedOrderId = null,
            read = false,
            createdAt = Instant.now(),
        )

    @BeforeEach
    fun setUp() {
        notificationService = mockk()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(NotificationController(notificationService))
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `GET list returns page`() {
        every { notificationService.listMine(userId, 0, 20) } returns
            PageResponse(listOf(sample), 0, 20, 1, 1)

        mockMvc
            .perform(get("/api/v1/notifications").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content[0].type").value("ORDER_PAID"))
    }

    @Test
    fun `GET unread-count returns number`() {
        every { notificationService.unreadCount(userId) } returns UnreadCountResponse(unread = 3)

        mockMvc
            .perform(get("/api/v1/notifications/unread-count").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unread").value(3))
    }

    @Test
    fun `POST mark read`() {
        every { notificationService.markRead(userId, sample.id) } returns sample.copy(read = true)

        mockMvc
            .perform(post("/api/v1/notifications/${sample.id}/read").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.read").value(true))
    }

    @Test
    fun `POST read-all`() {
        every { notificationService.markAllRead(userId) } returns UnreadCountResponse(unread = 0)

        mockMvc
            .perform(post("/api/v1/notifications/read-all").principal(auth))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.unread").value(0))

        verify(exactly = 1) { notificationService.markAllRead(userId) }
    }
}
