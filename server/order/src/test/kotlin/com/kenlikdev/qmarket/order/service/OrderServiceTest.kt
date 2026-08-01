package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.cart.domain.Cart
import com.kenlikdev.qmarket.cart.domain.CartItem
import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.domain.Product
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.domain.Address
import com.kenlikdev.qmarket.identity.repository.AddressRepository
import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
import com.kenlikdev.qmarket.order.repository.OrderRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class OrderServiceTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var cartRepository: CartRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var addressRepository: AddressRepository
    private lateinit var orderService: OrderService

    private val userId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    private val product =
        Product(
            id = productId,
            name = "Headphones",
            slug = "headphones",
            price = BigDecimal("50.00"),
            stockQuantity = 10,
            active = true,
        )

    @BeforeEach
    fun setUp() {
        orderRepository = mockk()
        cartRepository = mockk()
        productRepository = mockk()
        addressRepository = mockk()
        orderService = OrderService(orderRepository, cartRepository, productRepository, addressRepository)
    }

    @Test
    fun `createFromCart creates order and clears cart`() {
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(CartItem(cart = this, productId = productId, quantity = 2))
            }
        every { cartRepository.findByUserId(userId) } returns Optional.of(cart)
        every { productRepository.findById(productId) } returns Optional.of(product)
        every { productRepository.save(any()) } answers { firstArg() }
        every { orderRepository.save(any()) } answers {
            firstArg<Order>().also { it.id = UUID.randomUUID() }
        }
        every { cartRepository.save(any()) } answers { firstArg() }

        val result =
            orderService.createFromCart(
                userId,
                CreateOrderRequest(shippingAddress = "Moscow, Red Square 1"),
            )

        assertEquals(OrderStatus.PENDING, result.status)
        assertEquals(BigDecimal("100.00"), result.totalAmount)
        assertEquals(1, result.items.size)
        assertEquals(0, cart.items.size)
        verify { productRepository.save(match { it.stockQuantity == 8 }) }
    }

    @Test
    fun `createFromCart fails on empty cart`() {
        every { cartRepository.findByUserId(userId) } returns Optional.empty()

        assertThrows<BadRequestException> {
            orderService.createFromCart(userId, CreateOrderRequest(shippingAddress = "Address"))
        }
    }

    @Test
    fun `createFromCart fails on insufficient stock`() {
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(CartItem(cart = this, productId = productId, quantity = 100))
            }
        every { cartRepository.findByUserId(userId) } returns Optional.of(cart)
        every { productRepository.findById(productId) } returns Optional.of(product)

        assertThrows<BadRequestException> {
            orderService.createFromCart(userId, CreateOrderRequest(shippingAddress = "Address"))
        }
    }

    @Test
    fun `updateStatus changes status`() {
        val order =
            Order(
                id = UUID.randomUUID(),
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal.TEN,
            )
        every { orderRepository.findById(order.id!!) } returns Optional.of(order)
        every { orderRepository.save(any()) } answers { firstArg() }

        val result =
            orderService.updateStatus(
                order.id!!,
                UpdateOrderStatusRequest(OrderStatus.CONFIRMED),
            )

        assertEquals(OrderStatus.CONFIRMED, result.status)
    }

    @Test
    fun `payMock sets status to PAID`() {
        val orderId = UUID.randomUUID()
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal.TEN,
            )
        every { orderRepository.findByIdAndUserId(orderId, userId) } returns Optional.of(order)
        every { orderRepository.save(any()) } answers { firstArg() }

        val result = orderService.payMock(userId, orderId)

        assertEquals(OrderStatus.PAID, result.status)
    }

    @Test
    fun `payMock fails when already paid`() {
        val orderId = UUID.randomUUID()
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PAID,
                totalAmount = BigDecimal.TEN,
            )
        every { orderRepository.findByIdAndUserId(orderId, userId) } returns Optional.of(order)

        assertThrows<BadRequestException> {
            orderService.payMock(userId, orderId)
        }
    }

    @Test
    fun `payMock fails for other user order`() {
        val orderId = UUID.randomUUID()
        every { orderRepository.findByIdAndUserId(orderId, userId) } returns Optional.empty()

        assertThrows<NotFoundException> {
            orderService.payMock(userId, orderId)
        }
    }

    @Test
    fun `createFromCart resolves addressId to shipping text`() {
        val addressId = UUID.randomUUID()
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(CartItem(cart = this, productId = productId, quantity = 1))
            }
        val address =
            Address(
                id = addressId,
                userId = userId,
                recipientName = "Ivan",
                city = "Moscow",
                streetLine1 = "Tverskaya 1",
                postalCode = "101000",
                country = "RU",
            )
        every { cartRepository.findByUserId(userId) } returns Optional.of(cart)
        every { productRepository.findById(productId) } returns Optional.of(product)
        every { addressRepository.findByIdAndUserId(addressId, userId) } returns Optional.of(address)
        every { productRepository.save(any()) } answers { firstArg() }
        every { orderRepository.save(any()) } answers {
            firstArg<Order>().also { if (it.id == null) it.id = UUID.randomUUID() }
        }
        every { cartRepository.save(any()) } answers { firstArg() }

        val result =
            orderService.createFromCart(
                userId,
                CreateOrderRequest(addressId = addressId),
            )

        assertTrue(result.shippingAddress?.contains("Moscow") == true)
        assertTrue(result.shippingAddress?.contains("Tverskaya") == true)
    }

    @Test
    fun `createFromCart fails when neither address nor shipping given`() {
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(CartItem(cart = this, productId = productId, quantity = 1))
            }
        every { cartRepository.findByUserId(userId) } returns Optional.of(cart)

        assertThrows<BadRequestException> {
            orderService.createFromCart(userId, CreateOrderRequest())
        }
    }
}
