package com.kenlikdev.qmarket.cart.domain

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class CartTest {
    private val userId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    @Test
    fun `addItem creates line and merges quantity`() {
        val cart = Cart(userId = userId)
        cart.addItem(productId, 2, availableStock = 10, productSlug = "sku")
        assertEquals(1, cart.items.size)
        assertEquals(2, cart.items[0].quantity)

        cart.addItem(productId, 3, availableStock = 10, productSlug = "sku")
        assertEquals(1, cart.items.size)
        assertEquals(5, cart.items[0].quantity)
    }

    @Test
    fun `addItem rejects over stock`() {
        val cart = Cart(userId = userId)
        assertThrows<BadRequestException> {
            cart.addItem(productId, 5, availableStock = 3, productSlug = "sku")
        }
    }

    @Test
    fun `changeQuantity updates and removeItem clears line`() {
        val cart = Cart(userId = userId)
        cart.addItem(productId, 1, availableStock = 10, productSlug = "sku")
        cart.changeQuantity(productId, 4, availableStock = 10, productSlug = "sku")
        assertEquals(4, cart.items[0].quantity)

        cart.removeItem(productId)
        assertTrue(cart.isEmpty())
    }

    @Test
    fun `removeItem missing product throws`() {
        val cart = Cart(userId = userId)
        assertThrows<NotFoundException> {
            cart.removeItem(productId)
        }
    }
}
