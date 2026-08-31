package com.kenlikdev.qmarket.order.service

import com.kenlikdev.qmarket.cart.domain.Cart
import com.kenlikdev.qmarket.cart.domain.CartItem
import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.catalog.api.ProductInfo
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.NotFoundException
import com.kenlikdev.qmarket.identity.domain.Address
import com.kenlikdev.qmarket.identity.repository.AddressRepository
import com.kenlikdev.qmarket.order.domain.Order
import com.kenlikdev.qmarket.order.domain.OrderIdempotencyKey
import com.kenlikdev.qmarket.order.domain.OrderItem
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.dto.UpdateOrderStatusRequest
import com.kenlikdev.qmarket.order.repository.OrderIdempotencyKeyRepository
import com.kenlikdev.qmarket.order.repository.OrderRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID

class OrderServiceTest {
    private lateinit var orderRepository: OrderRepository
    private lateinit var cartRepository: CartRepository
    private lateinit var productCatalog: ProductCatalog
    private lateinit var addressRepository: AddressRepository
    private lateinit var idempotencyKeyRepository: OrderIdempotencyKeyRepository
    private lateinit var orderService: OrderService
    private lateinit var notificationService: NotificationService

    private val userId = UUID.randomUUID()
    private val productId = UUID.randomUUID()

    private val product =
        ProductInfo(
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
        productCatalog = mockk()
        addressRepository = mockk()
        idempotencyKeyRepository = mockk(relaxed = true)
        notificationService = mockk(relaxed = true)
        val transactionManager = mockk<PlatformTransactionManager>()
        val txStatus = mockk<TransactionStatus>(relaxed = true)
        every { transactionManager.getTransaction(any()) } returns txStatus
        every { transactionManager.commit(any()) } just Runs
        every { transactionManager.rollback(any()) } just Runs
        val entityManager = mockk<jakarta.persistence.EntityManager>(relaxed = true)
        val nativeQuery = mockk<jakarta.persistence.Query>(relaxed = true)
        every { entityManager.createNativeQuery(any<String>()) } returns nativeQuery
        every { nativeQuery.setParameter(any<String>(), any()) } returns nativeQuery
        every { nativeQuery.singleResult } returns 1
        orderService =
            OrderService(
                orderRepository,
                cartRepository,
                productCatalog,
                addressRepository,
                idempotencyKeyRepository,
                entityManager,
                notificationService,
                transactionManager,
            )
    }

    @Test
    fun `createFromCart creates order and clears cart`() {
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(CartItem(cart = this, productId = productId, quantity = 2))
            }
        every { cartRepository.findByUserId(userId) } returns cart
        every { productCatalog.requireActive(productId) } returns product
        every { productCatalog.decreaseStock(productId, any()) } returns Unit
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
        verify { productCatalog.decreaseStock(productId, 2) }
    }

    @Test
    fun `createFromCart fails on empty cart`() {
        every { cartRepository.findByUserId(userId) } returns null

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
        every { cartRepository.findByUserId(userId) } returns cart
        every { productCatalog.requireActive(productId) } returns product
        every { productCatalog.decreaseStock(productId, any()) } returns Unit

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
        every { orderRepository.findByIdAndUserId(orderId, userId) } returns order
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
        every { orderRepository.findByIdAndUserId(orderId, userId) } returns order

        assertThrows<BadRequestException> {
            orderService.payMock(userId, orderId)
        }
    }

    @Test
    fun `payMock fails for other user order`() {
        val orderId = UUID.randomUUID()
        every { orderRepository.findByIdAndUserId(orderId, userId) } returns null

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
        every { cartRepository.findByUserId(userId) } returns cart
        every { productCatalog.requireActive(productId) } returns product
        every { productCatalog.decreaseStock(productId, any()) } returns Unit
        every { addressRepository.findByIdAndUserId(addressId, userId) } returns address
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
        every { cartRepository.findByUserId(userId) } returns cart

        assertThrows<BadRequestException> {
            orderService.createFromCart(userId, CreateOrderRequest())
        }
    }

    @Test
    fun `admin cancel restocks inventory`() {
        val orderId = UUID.randomUUID()
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal.TEN,
            ).apply {
                items.add(
                    OrderItem(
                        order = this,
                        productId = productId,
                        productName = "Headphones",
                        productSlug = "headphones",
                        unitPrice = BigDecimal("50.00"),
                        quantity = 2,
                        lineTotal = BigDecimal("100.00"),
                    ),
                )
            }
        every { orderRepository.findById(orderId) } returns Optional.of(order)
        every { orderRepository.save(any()) } answers { firstArg() }
        every { productCatalog.increaseStock(productId, 2) } returns Unit

        val result =
            orderService.updateStatus(
                orderId,
                UpdateOrderStatusRequest(OrderStatus.CANCELLED),
            )

        assertEquals(OrderStatus.CANCELLED, result.status)
        verify(exactly = 1) { productCatalog.increaseStock(productId, 2) }
    }

    @Test
    fun `admin cannot ship from pending`() {
        val orderId = UUID.randomUUID()
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal.TEN,
            )
        every { orderRepository.findById(orderId) } returns Optional.of(order)

        assertThrows<BadRequestException> {
            orderService.updateStatus(
                orderId,
                UpdateOrderStatusRequest(OrderStatus.SHIPPED),
            )
        }
        verify(exactly = 0) { productCatalog.increaseStock(any(), any()) }
    }

    @Test
    fun `cancelMyOrder cancels then restocks`() {
        val orderId = UUID.randomUUID()
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal.TEN,
            ).apply {
                items.add(
                    OrderItem(
                        order = this,
                        productId = productId,
                        productName = "Headphones",
                        productSlug = "headphones",
                        unitPrice = BigDecimal("50.00"),
                        quantity = 1,
                        lineTotal = BigDecimal("50.00"),
                    ),
                )
            }
        every { orderRepository.findByIdAndUserId(orderId, userId) } returns order
        every { orderRepository.save(any()) } answers { firstArg() }
        every { productCatalog.increaseStock(productId, 1) } returns Unit

        val result = orderService.cancelMyOrder(userId, orderId)

        assertEquals(OrderStatus.CANCELLED, result.status)
        verify(exactly = 1) { productCatalog.increaseStock(productId, 1) }
    }

    @Test
    fun `createFromCart with same idempotency key returns existing order`() {
        val orderId = UUID.randomUUID()
        val existing =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PENDING,
                totalAmount = BigDecimal("100.00"),
                shippingAddress = "Moscow",
            )
        every { idempotencyKeyRepository.findByUserIdAndKey(userId, "key-1") } returns
            OrderIdempotencyKey(userId = userId, key = "key-1", orderId = orderId, requestHash = "")
        every { orderRepository.findById(orderId) } returns Optional.of(existing)

        val result =
            orderService.createFromCart(
                userId,
                CreateOrderRequest(shippingAddress = "Moscow"),
                idempotencyKey = "key-1",
            )

        assertEquals(orderId, result.id)
        verify(exactly = 0) { productCatalog.decreaseStock(any(), any()) }
        verify(exactly = 0) { cartRepository.findByUserId(any()) }
    }

    @Test
    fun `createFromCart stores idempotency key on first success`() {
        val cart =
            Cart(id = UUID.randomUUID(), userId = userId).apply {
                items.add(CartItem(cart = this, productId = productId, quantity = 1))
            }
        every { idempotencyKeyRepository.findByUserIdAndKey(userId, "checkout-abc") } returns null
        every { cartRepository.findByUserId(userId) } returns cart
        every { productCatalog.requireActive(productId) } returns product
        every { productCatalog.decreaseStock(productId, 1) } returns Unit
        every { orderRepository.save(any()) } answers {
            firstArg<Order>().also { it.id = UUID.randomUUID() }
        }
        every { cartRepository.save(any()) } answers { firstArg() }
        every { idempotencyKeyRepository.save(any()) } answers { firstArg() }

        orderService.createFromCart(
            userId,
            CreateOrderRequest(shippingAddress = "Moscow, Red Square 1"),
            idempotencyKey = "checkout-abc",
        )

        verify(exactly = 1) { idempotencyKeyRepository.save(match { it.key == "checkout-abc" && it.userId == userId }) }
    }

    @Test
    fun `idempotency key longer than 128 chars is rejected`() {
        assertThrows<BadRequestException> {
            orderService.createFromCart(
                userId,
                CreateOrderRequest(shippingAddress = "x"),
                idempotencyKey = "k".repeat(129),
            )
        }
    }

    @Test
    fun `createFromCart same key different body returns conflict`() {
        val orderId = UUID.randomUUID()
        val hash =
            orderService.requestFingerprint(
                CreateOrderRequest(shippingAddress = "Original Street"),
            )
        every { idempotencyKeyRepository.findByUserIdAndKey(userId, "key-dup") } returns
            OrderIdempotencyKey(
                userId = userId,
                key = "key-dup",
                orderId = orderId,
                requestHash = hash,
            )

        assertThrows<ConflictException> {
            orderService.createFromCart(
                userId,
                CreateOrderRequest(shippingAddress = "Different Street"),
                idempotencyKey = "key-dup",
            )
        }
        verify(exactly = 0) { cartRepository.findByUserId(any()) }
    }

    @Test
    fun `updateStatus notifies on transition`() {
        val orderId = UUID.randomUUID()
        val order =
            Order(
                id = orderId,
                userId = userId,
                status = OrderStatus.PAID,
                totalAmount = java.math.BigDecimal("10.00"),
            )
        every { orderRepository.findById(orderId) } returns java.util.Optional.of(order)
        every { orderRepository.save(any()) } answers { firstArg() }

        orderService.updateStatus(orderId, UpdateOrderStatusRequest(status = OrderStatus.SHIPPED))

        verify {
            notificationService.notifyOrderEvent(
                userId = userId,
                type = "ORDER_STATUS_SHIPPED",
                title = any(),
                body = any(),
                orderId = orderId,
            )
        }
    }
}
