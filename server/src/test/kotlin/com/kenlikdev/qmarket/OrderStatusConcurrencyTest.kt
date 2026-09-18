package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.order.domain.OrderStatus
import com.kenlikdev.qmarket.order.repository.OrderRepository
import com.kenlikdev.qmarket.order.service.OrderService
import com.kenlikdev.qmarket.support.TestJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
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

    private lateinit var token: String
    private lateinit var productId: String

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

        mockMvc.perform(
            delete("/api/v1/cart")
                .header("Authorization", "Bearer $token"),
        )
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
            } catch (_: org.springframework.orm.ObjectOptimisticLockingFailureException) {
                rejected.incrementAndGet()
            } catch (_: jakarta.persistence.OptimisticLockException) {
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
            } catch (_: org.springframework.orm.ObjectOptimisticLockingFailureException) {
                rejected.incrementAndGet()
            } catch (_: jakarta.persistence.OptimisticLockException) {
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

        val finalStatus = orderRepository.findById(UUID.fromString(orderId)).orElseThrow().status
        assertTrue(
            finalStatus == OrderStatus.PAID || finalStatus == OrderStatus.CANCELLED,
            "expected PAID or CANCELLED, got $finalStatus",
        )
        assertEquals(1, payOk.get() + cancelOk.get(), "exactly one transition should commit")
        assertTrue(rejected.get() >= 1, "loser should be rejected")
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

        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(UUID.fromString(orderId)).orElseThrow().status)
        assertEquals(1, cancelOk.get(), "only one cancel should commit")
        assertTrue(rejected.get() >= 1)
    }
}
