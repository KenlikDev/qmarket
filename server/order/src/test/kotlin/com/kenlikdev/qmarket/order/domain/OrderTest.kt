package com.kenlikdev.qmarket.order.domain

import com.kenlikdev.qmarket.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class OrderTest {
    private fun pending() = Order(userId = UUID.randomUUID(), status = OrderStatus.PENDING)

    @Test
    fun `cancel from pending`() {
        val order = pending()
        order.cancel()
        assertEquals(OrderStatus.CANCELLED, order.status)
    }

    @Test
    fun `cancel from paid fails`() {
        val order = pending().apply { status = OrderStatus.PAID }
        assertThrows<BadRequestException> { order.cancel() }
    }

    @Test
    fun `markPaid from pending`() {
        val order = pending()
        order.markPaid()
        assertEquals(OrderStatus.PAID, order.status)
    }

    @Test
    fun `admin cannot reopen cancelled`() {
        val order = pending().apply { status = OrderStatus.CANCELLED }
        assertThrows<BadRequestException> {
            order.applyAdminStatus(OrderStatus.CONFIRMED)
        }
    }
}
