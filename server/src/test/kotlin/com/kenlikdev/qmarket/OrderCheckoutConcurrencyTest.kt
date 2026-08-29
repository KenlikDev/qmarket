package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.cart.domain.Cart
import com.kenlikdev.qmarket.cart.domain.CartItem
import com.kenlikdev.qmarket.cart.repository.CartRepository
import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import com.kenlikdev.qmarket.order.dto.CreateOrderRequest
import com.kenlikdev.qmarket.order.repository.OrderRepository
import com.kenlikdev.qmarket.order.service.OrderService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    private lateinit var userId: UUID
    private lateinit var productId: UUID

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

        val cart = cartRepository.findByUserId(userId) ?: Cart(userId = userId)
        cart.items.clear()
        cart.items.add(CartItem(cart = cart, productId = productId, quantity = 1))
        cartRepository.save(cart)
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
            1,
            orderRepository
                .findByUserIdOrderByCreatedAtDesc(
                    userId,
                    org.springframework.data.domain.PageRequest
                        .of(0, 20),
                ).totalElements,
        )
    }
}
