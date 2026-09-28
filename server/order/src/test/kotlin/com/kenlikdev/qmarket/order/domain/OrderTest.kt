package com.kenlikdev.qmarket.order.domain

import com.kenlikdev.qmarket.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun `admin cannot skip to shipped from pending`() {
        val order = pending()
        assertThrows<BadRequestException> {
            order.applyAdminStatus(OrderStatus.SHIPPED)
        }
    }

    @Test
    fun `admin cancel from pending requests restock`() {
        val order = pending()
        assertTrue(order.applyAdminStatus(OrderStatus.CANCELLED))
        assertEquals(OrderStatus.CANCELLED, order.status)
    }

    @Test
    fun `admin paid to shipped allowed without restock`() {
        val order = pending().apply { status = OrderStatus.PAID }
        assertFalse(order.applyAdminStatus(OrderStatus.SHIPPED))
        assertEquals(OrderStatus.SHIPPED, order.status)
    }

    @Test
    fun `transition matrix happy path`() {
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED))
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAID))
        assertTrue(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED))
        assertFalse(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED))
        assertTrue(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PAID))
        assertTrue(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED))
        assertFalse(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.DELIVERED))
        assertTrue(OrderStatus.PAID.canTransitionTo(OrderStatus.SHIPPED))
        assertFalse(OrderStatus.PAID.canTransitionTo(OrderStatus.CANCELLED))
        assertTrue(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED))
        assertFalse(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED))
    }
}
