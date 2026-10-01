package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.repository.OrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFailsWith

class UnpaidOrderExpirySchedulerTest {
    private lateinit var repository: OrderRepository
    private lateinit var orderService: OrderService
    private lateinit var properties: PaymentExpiryProperties
    private lateinit var scheduler: UnpaidOrderExpiryScheduler

    @BeforeEach
    fun setUp() {
        repository = mockk()
        orderService = mockk()
        properties = PaymentExpiryProperties().apply { batchSize = 10 }
        scheduler = UnpaidOrderExpiryScheduler(repository, orderService, properties)
    }

    @Test
    fun `expires due unpaid orders`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every {
            repository.findExpiredUnpaidOrderIds(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = any(),
                pageable = any(),
            )
        } returns listOf(first, second)
        every { orderService.expireUnpaidOrder(any()) } returns mockk(relaxed = true)

        scheduler.expireDueOrders()

        verify(exactly = 1) { orderService.expireUnpaidOrder(first) }
        verify(exactly = 1) { orderService.expireUnpaidOrder(second) }
    }

    @Test
    fun `continues after one order has already changed state`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every {
            repository.findExpiredUnpaidOrderIds(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = any(),
                pageable = any(),
            )
        } returns listOf(first, second)
        every { orderService.expireUnpaidOrder(first) } throws BadRequestException("already paid")
        every { orderService.expireUnpaidOrder(second) } returns mockk(relaxed = true)

        scheduler.expireDueOrders()

        verify(exactly = 1) { orderService.expireUnpaidOrder(second) }
    }

    @Test
    fun `logs and continues after unexpected failure`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every {
            repository.findExpiredUnpaidOrderIds(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = any(),
                pageable = any(),
            )
        } returns listOf(first, second)
        every { orderService.expireUnpaidOrder(first) } throws IllegalStateException("database unavailable")
        every { orderService.expireUnpaidOrder(second) } returns mockk(relaxed = true)

        scheduler.expireDueOrders()

        verify(exactly = 1) { orderService.expireUnpaidOrder(second) }
    }
}
