package com.kenlikdev.qmarket.cart.service

import com.kenlikdev.qmarket.cart.domain.Cart
import com.kenlikdev.qmarket.cart.domain.CartItem
import com.kenlikdev.qmarket.cart.dto.AddCartItemRequest
import com.kenlikdev.qmarket.cart.dto.UpdateCartItemRequest
import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class CartServiceTest {
    private lateinit var cartRepository: CartRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var cartService: CartService

    private val userId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    private val product =
        Product(
            id = productId,
            name = "Headphones",
            slug = "headphones",
            price = BigDecimal("99.99"),
            stockQuantity = 10,
            active = true,
        )

    @BeforeEach
    fun setUp() {
        cartRepository = mockk()
        productRepository = mockk()
        cartService = CartService(cartRepository, productRepository)
    }

    @Test
    fun `getCart returns empty cart when none exists`() {
        every { cartRepository.findByUserId(userId) } returns Optional.empty()

        val result = cartService.getCart(userId)

        assertEquals(0, result.totalItems)
        assertEquals(BigDecimal.ZERO, result.totalPrice)
        assertEquals(userId, result.userId)
    }

    @Test
    fun `addItem creates cart and adds product`() {
        every { productRepository.findById(productId) } returns Optional.of(product)
        every { cartRepository.findByUserId(userId) } returns Optional.empty()
        every { cartRepository.save(any()) } answers { firstArg() }
        every { productRepository.findAllById(any<Iterable<UUID>>()) } returns listOf(product)

        val result = cartService.addItem(userId, AddCartItemRequest(productId, 2))

        assertEquals(2, result.totalItems)
        assertEquals(1, result.items.size)
        assertEquals(productId, result.items[0].productId)
        verify { cartRepository.save(any()) }
    }

    @Test
    fun `addItem rejects quantity above stock`() {
        every { productRepository.findById(productId) } returns Optional.of(product)
        every { cartRepository.findByUserId(userId) } returns Optional.empty()
        every { cartRepository.save(any()) } answers { firstArg() }

        assertThrows<BadRequestException> {
            cartService.addItem(userId, AddCartItemRequest(productId, 50))
        }
    }

    @Test
    fun `updateItem changes quantity`() {
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(
                    CartItem(id = UUID.randomUUID(), cart = this, productId = productId, quantity = 1),
                )
            }
        every { productRepository.findById(productId) } returns Optional.of(product)
        every { cartRepository.findByUserId(userId) } returns Optional.of(cart)
        every { cartRepository.save(any()) } answers { firstArg() }
        every { productRepository.findAllById(any<Iterable<UUID>>()) } returns listOf(product)

        val result = cartService.updateItem(userId, productId, UpdateCartItemRequest(3))

        assertEquals(3, result.totalItems)
        assertEquals(3, result.items[0].quantity)
    }

    @Test
    fun `removeItem removes product from cart`() {
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(
                    CartItem(id = UUID.randomUUID(), cart = this, productId = productId, quantity = 2),
                )
            }
        every { cartRepository.findByUserId(userId) } returns Optional.of(cart)
        every { cartRepository.save(any()) } answers { firstArg() }
        every { productRepository.findAllById(any<Iterable<UUID>>()) } returns emptyList()

        val result = cartService.removeItem(userId, productId)

        assertEquals(0, result.totalItems)
        assertEquals(0, result.items.size)
    }

    @Test
    fun `removeItem throws when product not in cart`() {
        val cart = Cart(id = UUID.randomUUID(), userId = userId)
        every { cartRepository.findByUserId(userId) } returns Optional.of(cart)

        assertThrows<NotFoundException> {
            cartService.removeItem(userId, productId)
        }
    }
}
