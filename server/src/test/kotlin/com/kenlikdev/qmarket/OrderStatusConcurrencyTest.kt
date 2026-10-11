package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.catalog.repository.ProductRepository
import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.domain.PaymentOperationState
import com.kenlikdev.qmarket.order.repository.OrderRepository
import com.kenlikdev.qmarket.order.service.OrderService
import com.kenlikdev.qmarket.support.TestJson
import jakarta.persistence.OptimisticLockException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Concurrent cancel vs pay must not both "succeed" into contradictory domain state
 * (optimistic lock / status machine). Exactly one terminal outcome wins.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderStatusConcurrencyTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var productRepository: ProductRepository

    private lateinit var token: String
    private lateinit var productId: String
    private var originalStock: Int = 0
    private val createdOrderIds = mutableListOf<UUID>()

    @BeforeEach
    fun setUp() {
        val loginResult =
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"admin@qmarket.local","password":"admin123"}"""),
                ).andExpect(status().isOk)
                .andReturn()
        token = TestJson.accessToken(loginResult.response.contentAsString)

        val productsJson =
            mockMvc
                .perform(get("/api/v1/products"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        productId = TestJson.firstContentId(productsJson)
        originalStock =
            productRepository
                .findById(UUID.fromString(productId))
                .orElseThrow()
                .stockQuantity

        mockMvc.perform(
            delete("/api/v1/cart")
                .header("Authorization", "Bearer $token"),
        )
    }

    @AfterEach
    fun tearDown() {
        createdOrderIds.forEach(orderRepository::deleteById)
        productRepository.findById(UUID.fromString(productId)).ifPresent { product ->
            product.stockQuantity = originalStock
            productRepository.saveAndFlush(product)
        }
    }

    @Test
    fun `concurrent cancel and pay leave a single consistent status`() {
        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":1}"""),
            ).andExpect(status().isOk)

        val create =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"shippingAddress":"Race St 1"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
        val orderId = TestJson.id(create.response.contentAsString)
        createdOrderIds += UUID.fromString(orderId)
        val userId = orderRepository.findById(UUID.fromString(orderId)).orElseThrow().userId

        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val payOk = AtomicInteger(0)
        val cancelOk = AtomicInteger(0)
        val rejected = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(2)

        pool.submit {
            try {
                start.await()
                orderService.pay(userId, UUID.fromString(orderId))
                payOk.incrementAndGet()
            } catch (_: BadRequestException) {
                rejected.incrementAndGet()
            } catch (_: ObjectOptimisticLockingFailureException) {
                rejected.incrementAndGet()
            } catch (_: OptimisticLockException) {
                rejected.incrementAndGet()
            } catch (_: Exception) {
                rejected.incrementAndGet()
            } finally {
                done.countDown()
            }
        }
        pool.submit {
            try {
                start.await()
                orderService.cancelMyOrder(userId, UUID.fromString(orderId))
                cancelOk.incrementAndGet()
            } catch (_: BadRequestException) {
                rejected.incrementAndGet()
            } catch (_: ObjectOptimisticLockingFailureException) {
                rejected.incrementAndGet()
            } catch (_: OptimisticLockException) {
                rejected.incrementAndGet()
            } catch (_: Exception) {
                rejected.incrementAndGet()
            } finally {
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        val finalOrder = orderRepository.findById(UUID.fromString(orderId)).orElseThrow()
        val finalStatus = finalOrder.status
        assertTrue(
            finalStatus == OrderStatus.PAID || finalStatus == OrderStatus.CANCELLED,
            "expected PAID or CANCELLED, got $finalStatus",
        )
        assertEquals(1, payOk.get() + cancelOk.get(), "exactly one transition should commit")
        assertTrue(rejected.get() >= 1, "loser should be rejected")
        assertEquals(
            PaymentOperationState.NONE,
            finalOrder.paymentOperationState,
            "terminal orders must not retain an in-flight payment operation",
        )
    }

    @Test
    fun `double concurrent cancel restocks at most once`() {
        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":1}"""),
            ).andExpect(status().isOk)

        val create =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"shippingAddress":"Race St 2"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
        val orderId = TestJson.id(create.response.contentAsString)
        val userId = orderRepository.findById(UUID.fromString(orderId)).orElseThrow().userId

        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val cancelOk = AtomicInteger(0)
        val rejected = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(2)

        repeat(2) {
            pool.submit {
                try {
                    start.await()
                    orderService.cancelMyOrder(userId, UUID.fromString(orderId))
                    cancelOk.incrementAndGet()
                } catch (_: Exception) {
                    rejected.incrementAndGet()
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS))
        pool.shutdown()

        val finalOrder = orderRepository.findById(UUID.fromString(orderId)).orElseThrow()
        assertEquals(OrderStatus.CANCELLED, finalOrder.status)
        assertEquals(2, cancelOk.get() + rejected.get(), "both callers must complete")
        assertTrue(cancelOk.get() in 1..2, "at least one cancellation caller must complete the terminal transition")
        assertEquals(
            PaymentOperationState.NONE,
            finalOrder.paymentOperationState,
            "cancelled orders must not retain an in-flight payment operation",
        )
        val finalStock =
            productRepository
                .findById(UUID.fromString(productId))
                .orElseThrow()
                .stockQuantity
        assertEquals(originalStock, finalStock, "inventory must be restored exactly once")
    }

    @Test
    fun `expired order racing with provider confirmation restores stock only if cancellation wins`() {
        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":1}"""),
            ).andExpect(status().isOk)

        val create =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"shippingAddress":"Expiry race street"}"""),
                ).andExpect(status().isCreated)
                .andReturn()
        val orderId = TestJson.id(create.response.contentAsString)
        val orderUuid = UUID.fromString(orderId)
        createdOrderIds += orderUuid
        val order = orderRepository.findById(orderUuid).orElseThrow()
        order.paymentProvider = "mock"
        order.paymentExpiresAt = Instant.now().minusSeconds(60)
        orderRepository.saveAndFlush(order)

        val stockAfterReservation =
            productRepository
                .findById(UUID.fromString(productId))
                .orElseThrow()
                .stockQuantity

        val start = CountDownLatch(1)
        val done = CountDownLatch(2)
        val paymentCommitted = AtomicInteger(0)
        val expiryCommitted = AtomicInteger(0)
        val rejected = AtomicInteger(0)
        val pool = Executors.newFixedThreadPool(2)

        pool.submit {
            try {
                start.await()
                orderService.markPaidFromProvider(
                    orderId = orderUuid,
                    providerId = "mock",
                    providerReference = "provider-confirmed-$orderId",
                )
                paymentCommitted.incrementAndGet()
            } catch (_: Exception) {
                rejected.incrementAndGet()
            } finally {
                done.countDown()
            }
        }
        pool.submit {
            try {
                start.await()
                orderService.expireUnpaidOrder(orderUuid)
                expiryCommitted.incrementAndGet()
            } catch (_: Exception) {
                rejected.incrementAndGet()
            } finally {
                done.countDown()
            }
        }

        start.countDown()
        assertTrue(done.await(30, TimeUnit.SECONDS), "expiry/provider workers timed out")
        pool.shutdown()

        val finalOrder = orderRepository.findById(orderUuid).orElseThrow()
        assertTrue(
            finalOrder.status == OrderStatus.PAID || finalOrder.status == OrderStatus.CANCELLED,
            "expected PAID or CANCELLED, got ${finalOrder.status}",
        )
        assertEquals(
            1,
            paymentCommitted.get() + expiryCommitted.get(),
            "exactly one competing terminal transition should commit",
        )
        assertTrue(rejected.get() >= 1, "the losing transition should be rejected")
        assertEquals(
            PaymentOperationState.NONE,
            finalOrder.paymentOperationState,
            "terminal order must not retain an in-flight payment operation",
        )

        val finalStock =
            productRepository
                .findById(UUID.fromString(productId))
                .orElseThrow()
                .stockQuantity
        if (finalOrder.status == OrderStatus.CANCELLED) {
            assertEquals(originalStock, finalStock, "expiry cancellation must restore stock exactly once")
        } else {
            assertEquals(
                stockAfterReservation,
                finalStock,
                "confirmed payment must not restore stock reserved for the paid order",
            )
        }
    }
}
