package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.cart.domain.Cart
import com.kenlikdev.qmarket.cart.domain.CartItem
import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.api.ProductCatalog
import com.kenlikdev.qmarket.catalog.api.ProductInfo
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.repository.OrderRepository
import com.kenlikdev.qmarket.order.service.OrderService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Concurrent checkout against real OrderService + Testcontainers Postgres.
 * Does **not** use MockMvc (not thread-safe). Verifies Stripe-style idempotency:
 * same Idempotency-Key → exactly one order id for all successful callers.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderCheckoutConcurrencyTest {
    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var cartRepository: CartRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    @Autowired
    private lateinit var productCatalog: ProductCatalog

    @Autowired
    private lateinit var transactionManager: org.springframework.transaction.PlatformTransactionManager

    private lateinit var userId: UUID
    private lateinit var productId: UUID
    private var originalStock: Int = 0
    private lateinit var originalPrice: BigDecimal
    private val createdOrderIds = mutableSetOf<UUID>()
    private var initialOrderCount: Long = 0

    @BeforeEach
    fun setUp() {
        val user =
            userRepository.findByEmail("admin@qmarket.local")
                ?: error("seed admin missing — enable qmarket.seed in application-test.yml")
        userId = user.id ?: error("user id null")
        val product =
            productRepository.findAll().firstOrNull { it.active }
                ?: error("seed product missing")
        productId = product.id ?: error("product id null")
        originalStock = product.stockQuantity
        originalPrice = product.price
        if (originalStock < 1) {
            product.stockQuantity = 10
            productRepository.saveAndFlush(product)
            originalStock = 10
        }
        initialOrderCount =
            orderRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(userId, PageRequest.of(0, 100))
                .totalElements

        val cart = cartRepository.findByUserIdWithItems(userId) ?: Cart(userId = userId)
        cart.items.clear()
        cart.items.add(CartItem(cart = cart, productId = productId, quantity = 1))
        cartRepository.save(cart)
    }

    @AfterEach
    fun tearDown() {
        synchronized(createdOrderIds) { createdOrderIds.toList() }.forEach(orderRepository::deleteById)
        cartRepository.findByUserIdWithItems(userId)?.let { cart ->
            cart.clearItems()
            cartRepository.save(cart)
        }
        productRepository.findById(productId).ifPresent { product ->
            product.stockQuantity = originalStock
            product.price = originalPrice
            productRepository.saveAndFlush(product)
        }
    }

    @Test
    fun `concurrent createFromCart same key yields one order`() {
        val threads = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val successes = AtomicInteger(0)
        val orderIds = ConcurrentHashMap.newKeySet<String>()
        val errors = ConcurrentHashMap.newKeySet<String>()
        val idemKey = "svc-concurrent-" + System.nanoTime()
        val pool = Executors.newFixedThreadPool(threads)

        repeat(threads) {
            pool.submit {
                try {
                    start.await()
                    val response =
                        orderService.createFromCart(
                            userId,
                            CreateOrderRequest(shippingAddress = "Concurrent Lane 1"),
                            idempotencyKey = idemKey,
                        )
                    successes.incrementAndGet()
                    orderIds.add(response.id.toString())
                    synchronized(createdOrderIds) { createdOrderIds += response.id }
                } catch (e: Exception) {
                    errors.add(e.javaClass.simpleName + ": " + (e.message ?: ""))
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(60, TimeUnit.SECONDS), "workers timed out")
        pool.shutdown()

        assertEquals(
            emptySet<String>(),
            errors,
            "no worker should fail when Idempotency-Key is shared (got $errors)",
        )
        assertEquals(threads, successes.get(), "all workers should get the same order response")
        assertEquals(1, orderIds.size, "exactly one order id")
        assertEquals(
            initialOrderCount + 1,
            orderRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(
                    userId,
                    PageRequest.of(0, 100),
                ).totalElements,
        )
    }

    @Test
    fun `concurrent createFromCart without key cannot create two orders`() {
        val threads = 2
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val successes = AtomicInteger(0)
        val errors = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(threads)

        repeat(threads) {
            pool.submit {
                try {
                    start.await()
                    val response =
                        orderService.createFromCart(
                            userId,
                            CreateOrderRequest(shippingAddress = "Lock Lane 1"),
                        )
                    synchronized(createdOrderIds) { createdOrderIds += response.id }
                    successes.incrementAndGet()
                } catch (e: Exception) {
                    errors.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()

        assertTrue(done.await(60, TimeUnit.SECONDS), "workers timed out")
        pool.shutdown()

        assertEquals(1, successes.get(), "exactly one checkout should consume the cart")
        assertEquals(1, errors.get(), "the concurrent checkout should observe an empty cart")
        assertEquals(
            initialOrderCount + 1,
            orderRepository
                .findByUserIdOrderByCreatedAtDescIdDesc(
                    userId,
                    PageRequest.of(0, 100),
                ).totalElements,
        )
    }

    @Test
    fun `checkout reservation snapshots price after acquiring the product lock`() {
        val transactionTemplate = TransactionTemplate(transactionManager)
        val reservationReady = CountDownLatch(1)
        val adminStarted = CountDownLatch(1)
        val allowCheckoutCommit = CountDownLatch(1)
        val snapshot = AtomicReference<ProductInfo?>()
        val failures = ConcurrentHashMap.newKeySet<String>()
        val pool = Executors.newFixedThreadPool(2)

        try {
            pool.submit {
                try {
                    transactionTemplate.executeWithoutResult {
                        snapshot.set(productCatalog.reserveStock(productId, 1))
                        reservationReady.countDown()
                        assertTrue(
                            allowCheckoutCommit.await(30, TimeUnit.SECONDS),
                            "checkout transaction release timed out",
                        )
                    }
                } catch (e: Exception) {
                    failures.add("checkout: ${e.javaClass.simpleName}: ${e.message ?: ""}")
                    reservationReady.countDown()
                }
            }

            assertTrue(reservationReady.await(30, TimeUnit.SECONDS), "checkout reservation timed out")

            pool.submit {
                try {
                    adminStarted.countDown()
                    transactionTemplate.executeWithoutResult {
                        val product =
                            productRepository.findByIdForUpdate(productId)
                                ?: error("seed product missing")
                        product.price = BigDecimal("999.99")
                        productRepository.saveAndFlush(product)
                    }
                } catch (e: Exception) {
                    failures.add("admin: ${e.javaClass.simpleName}: ${e.message ?: ""}")
                }
            }

            assertTrue(adminStarted.await(30, TimeUnit.SECONDS), "admin update did not start")
            allowCheckoutCommit.countDown()

            pool.shutdown()
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "workers timed out")

            assertTrue(failures.isEmpty(), "unexpected concurrency failures: $failures")
            assertEquals(
                originalPrice,
                snapshot.get()?.price,
                "checkout price must be captured while holding the product row lock",
            )
            assertEquals(
                BigDecimal("999.99"),
                productRepository.findById(productId).orElseThrow().price,
                "admin update must commit after the checkout lock is released",
            )
            assertEquals(
                originalStock - 1,
                productRepository.findById(productId).orElseThrow().stockQuantity,
            )
        } finally {
            pool.shutdownNow()
        }
    }
}
