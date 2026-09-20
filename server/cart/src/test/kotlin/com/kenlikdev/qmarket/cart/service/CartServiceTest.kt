package com.kenlikdev.qmarket.cart.service

import com.kenlikdev.qmarket.cart.domain.Cart
import com.kenlikdev.qmarket.cart.domain.CartItem
import com.kenlikdev.qmarket.cart.dto.AddCartItemRequest
import com.kenlikdev.qmarket.cart.dto.UpdateCartItemRequest
import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.catalog.api.ProductInfo
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
import java.util.UUID

class CartServiceTest {
    private lateinit var cartRepository: CartRepository
    private lateinit var productCatalog: ProductCatalog
    private lateinit var cartService: CartService

    private val userId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    private val product =
        ProductInfo(
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
        productCatalog = mockk()
        cartService = CartService(cartRepository, productCatalog)
    }

    @Test
    fun `getCart creates an empty cart when none exists`() {
        val cart = Cart(id = UUID.randomUUID(), userId = userId)
        every { cartRepository.findByUserId(userId) } returnsMany listOf(null, cart)
        every { cartRepository.insertIfMissing(userId) } returns 1
        every { productCatalog.findByIds(any()) } returns emptyMap()

        val result = cartService.getCart(userId)

        assertEquals(cart.id, result.id)
        assertEquals(0, result.totalItems)
        assertEquals(BigDecimal.ZERO, result.totalPrice)
        assertEquals(userId, result.userId)
        verify(exactly = 1) { cartRepository.insertIfMissing(userId) }
    }

    @Test
    fun `addItem creates cart and adds product`() {
        every { productCatalog.requireActive(productId) } returns product
        every { cartRepository.findByUserId(userId) } returns null
        every { cartRepository.insertIfMissing(userId) } returns 1
        every { cartRepository.save(any()) } answers { firstArg() }
        every { productCatalog.findByIds(any()) } returns mapOf(productId to product)

        val result = cartService.addItem(userId, AddCartItemRequest(productId, 2))

        assertEquals(2, result.totalItems)
        assertEquals(1, result.items.size)
        assertEquals(productId, result.items[0].productId)
        verify(exactly = 1) { cartRepository.insertIfMissing(userId) }
        verify(exactly = 2) { cartRepository.findByUserId(userId) }
    }

    @Test
    fun `addItem rejects quantity above stock`() {
        every { productCatalog.requireActive(productId) } returns product
        every { cartRepository.findByUserId(userId) } returns null
        every { cartRepository.insertIfMissing(userId) } returns 1
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
                    CartItem(
                        id = UUID.randomUUID(),
                        cart = this,
                        productId = productId,
                        quantity = 1,
                    ),
                )
            }
        every { productCatalog.requireActive(productId) } returns product
        every { cartRepository.findByUserId(userId) } returns cart
        every { cartRepository.save(any()) } answers { firstArg() }
        every { productCatalog.findByIds(any()) } returns mapOf(productId to product)

        val result = cartService.updateItem(userId, productId, UpdateCartItemRequest(3))

        assertEquals(3, result.totalItems)
        assertEquals(3, result.items[0].quantity)
    }

    @Test
    fun `removeItem removes product from cart`() {
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(
                    CartItem(
                        id = UUID.randomUUID(),
                        cart = this,
                        productId = productId,
                        quantity = 2,
                    ),
                )
            }
        every { cartRepository.findByUserId(userId) } returns cart
        every { cartRepository.save(any()) } answers { firstArg() }
        every { productCatalog.findByIds(any()) } returns emptyMap()

        val result = cartService.removeItem(userId, productId)

        assertEquals(0, result.totalItems)
        assertEquals(0, result.items.size)
    }

    @Test
    fun `removeItem throws when product not in cart`() {
        val cart = Cart(id = UUID.randomUUID(), userId = userId)
        every { cartRepository.findByUserId(userId) } returns cart

        assertThrows<NotFoundException> {
            cartService.removeItem(userId, productId)
        }
    }

    @Test
    fun `addItem is safe when another transaction creates the cart first`() {
        val raceProductId = UUID.randomUUID()
        val raceProduct =
            ProductInfo(
                id = raceProductId,
                name = "Widget",
                slug = "widget",
                price = BigDecimal("9.99"),
                stockQuantity = 10,
                active = true,
            )
        val existing = Cart(id = UUID.randomUUID(), userId = userId)

        every { productCatalog.requireActive(raceProductId) } returns raceProduct
        every { productCatalog.findByIds(any()) } returns mapOf(raceProductId to raceProduct)
        every { cartRepository.findByUserId(userId) } returnsMany listOf(null, existing)
        every { cartRepository.insertIfMissing(userId) } returns 0
        every { cartRepository.save(any()) } answers { firstArg() }

        val response =
            cartService.addItem(
                userId,
                AddCartItemRequest(productId = raceProductId, quantity = 1),
            )

        assertEquals(1, response.items.size)
        assertEquals(raceProductId, response.items[0].productId)
        verify(exactly = 1) { cartRepository.insertIfMissing(userId) }
    }

    @Test
    fun `findOrCreate uses atomic insert and reread`() {
        val raceProduct =
            ProductInfo(
                id = productId,
                name = "Phone",
                slug = "phone",
                price = BigDecimal("10.00"),
                stockQuantity = 5,
                active = true,
            )
        val cart = Cart(id = UUID.randomUUID(), userId = userId)

        every { productCatalog.requireActive(productId) } returns raceProduct
        every { cartRepository.findByUserId(userId) } returnsMany listOf(null, cart)
        every { cartRepository.insertIfMissing(userId) } returns 1
        every { cartRepository.save(any()) } answers { firstArg() }
        every { productCatalog.findByIds(any()) } returns mapOf(productId to raceProduct)

        val result = cartService.addItem(userId, AddCartItemRequest(productId, 1))

        assertEquals(cart.id, result.id)
        verify(exactly = 1) { cartRepository.insertIfMissing(userId) }
    }
}
