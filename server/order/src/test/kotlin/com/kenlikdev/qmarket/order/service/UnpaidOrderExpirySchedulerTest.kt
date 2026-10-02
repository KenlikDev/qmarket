package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.dto.OrderResponse
import com.kenlikdev.qmarket.order.repository.OrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

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
            repository.findOrdersRequiringPaymentRecovery(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = any(),
                staleBefore = any(),
                pageable = any(),
            )
        } returns listOf(first, second)
        every { orderService.recoverStalePaymentOperation(any()) } returns mockk<OrderResponse>(relaxed = true)

        scheduler.expireDueOrders()

        verify(exactly = 1) { orderService.recoverStalePaymentOperation(first) }
        verify(exactly = 1) { orderService.recoverStalePaymentOperation(second) }
    }

    @Test
    fun `continues after one order has already changed state`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every {
            repository.findOrdersRequiringPaymentRecovery(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = any(),
                staleBefore = any(),
                pageable = any(),
            )
        } returns listOf(first, second)
        every { orderService.recoverStalePaymentOperation(first) } throws BadRequestException("already paid")
        every { orderService.recoverStalePaymentOperation(second) } returns mockk<OrderResponse>(relaxed = true)

        scheduler.expireDueOrders()

        verify(exactly = 1) { orderService.recoverStalePaymentOperation(second) }
    }

    @Test
    fun `logs and continues after unexpected failure`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every {
            repository.findOrdersRequiringPaymentRecovery(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = any(),
                staleBefore = any(),
                pageable = any(),
            )
        } returns listOf(first, second)
        every { orderService.recoverStalePaymentOperation(first) } throws IllegalStateException("database unavailable")
        every { orderService.recoverStalePaymentOperation(second) } returns mockk<OrderResponse>(relaxed = true)

        scheduler.expireDueOrders()

        verify(exactly = 1) { orderService.recoverStalePaymentOperation(second) }
    }

    @Test
    fun `recovers stale payment creation`() {
        val staleOrder = UUID.randomUUID()
        every {
            repository.findOrdersRequiringPaymentRecovery(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = any(),
                staleBefore = any(),
                pageable = any(),
            )
        } returns listOf(staleOrder)
        every { orderService.recoverStalePaymentOperation(staleOrder) } returns mockk(relaxed = true)

        scheduler.expireDueOrders()

        verify(exactly = 1) { orderService.recoverStalePaymentOperation(staleOrder) }
    }

    @Test
    fun `retries stale cancellation operations`() {
        val staleOrder = UUID.randomUUID()
        every {
            repository.findOrdersRequiringPaymentRecovery(
                statuses = setOf(OrderStatus.PENDING, OrderStatus.CONFIRMED),
                now = any(),
                staleBefore = any(),
                pageable = any(),
            )
        } returns listOf(staleOrder)
        every { orderService.recoverStalePaymentOperation(staleOrder) } returns mockk<OrderResponse>(relaxed = true)

        scheduler.expireDueOrders()

        verify(exactly = 1) { orderService.recoverStalePaymentOperation(staleOrder) }
    }
}
