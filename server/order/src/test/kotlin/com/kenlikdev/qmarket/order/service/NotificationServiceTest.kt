package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.order.domain.UserNotification
import com.kenlikdev.qmarket.order.repository.UserNotificationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.util.UUID

class NotificationServiceTest {
    private lateinit var repo: UserNotificationRepository
    private lateinit var service: NotificationService
    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        repo = mockk()
        service = NotificationService(repo)
    }

    @Test
    fun notifyOrderEventPersistsRow() {
        val slot = slot<UserNotification>()
        every { repo.save(capture(slot)) } answers { firstArg() }

        service.notifyOrderEvent(userId, "ORDER_PLACED", "Order placed", "body", UUID.randomUUID())

        assertEquals("ORDER_PLACED", slot.captured.type)
        assertEquals(userId, slot.captured.userId)
        assertFalse(slot.captured.read)
        verify(exactly = 1) { repo.save(any()) }
    }

    @Test
    fun listMineMapsPage() {
        val n =
            UserNotification(
                id = UUID.randomUUID(),
                userId = userId,
                type = "ORDER_PAID",
                title = "Paid",
                body = "ok",
            )
        every {
            repo.findByUserIdOrderByCreatedAtDesc(userId, any())
        } returns PageImpl(listOf(n), PageRequest.of(0, 20), 1)

        val page = service.listMine(userId, 0, 20)
        assertEquals(1, page.content.size)
        assertEquals("ORDER_PAID", page.content[0].type)
        assertEquals("Paid", page.content[0].title)
    }
}
