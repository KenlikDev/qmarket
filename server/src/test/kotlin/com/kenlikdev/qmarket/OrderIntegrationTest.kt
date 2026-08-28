package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.support.TestJson
import org.hamcrest.Matchers.greaterThan
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Full-stack checkout path: auth → cart → order → list/cancel/pay.
 * DB: Testcontainers via jdbc:tc:postgresql in application-test.yml (no @Container needed).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

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
    fun `orders require authentication`() {
        mockMvc
            .perform(get("/api/v1/orders"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `create from empty cart returns bad request`() {
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"shippingAddress":"Test Street 1"}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `checkout list and cancel order`() {
        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":1}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(1))

        val createBody =
            """
            {
              "shippingAddress": "Integration Test Ave 42",
              "customerNote": "leave at door"
            }
            """.trimIndent()

        val createResult =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.shippingAddress").value("Integration Test Ave 42"))
                .andExpect(jsonPath("$.items").isArray)
                .andExpect(jsonPath("$.items[0].productId").value(productId))
                .andExpect(jsonPath("$.totalAmount").isNumber)
                .andReturn()

        val orderId = TestJson.id(createResult.response.contentAsString)

        mockMvc
            .perform(
                get("/api/v1/cart")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(0))

        mockMvc
            .perform(
                get("/api/v1/orders")
                    .header("Authorization", "Bearer $token")
                    .param("page", "0")
                    .param("size", "20"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))

        mockMvc
            .perform(
                get("/api/v1/orders/$orderId")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(orderId))
            .andExpect(jsonPath("$.status").value("PENDING"))

        mockMvc
            .perform(
                post("/api/v1/orders/$orderId/cancel")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CANCELLED"))
    }

    @Test
    fun `admin can list orders and update status`() {
        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":1}"""),
            ).andExpect(status().isOk)

        val createResult =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"shippingAddress":"Admin path address"}"""),
                ).andExpect(status().isCreated)
                .andReturn()

        val orderId = TestJson.id(createResult.response.contentAsString)

        mockMvc
            .perform(
                get("/api/v1/orders/admin/all")
                    .header("Authorization", "Bearer $token")
                    .param("page", "0")
                    .param("size", "20"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)

        mockMvc
            .perform(
                put("/api/v1/orders/admin/$orderId/status")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"status":"CONFIRMED"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
    }

    @Test
    fun `pay marks order as PAID`() {
        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":1}"""),
            ).andExpect(status().isOk)

        val createResult =
            mockMvc
                .perform(
                    post("/api/v1/orders")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"shippingAddress":"Pay Test Street"}"""),
                ).andExpect(status().isCreated)
                .andReturn()

        val orderId = TestJson.id(createResult.response.contentAsString)

        mockMvc
            .perform(
                post("/api/v1/orders/$orderId/pay")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PAID"))
    }

    @Test
    fun `concurrent checkout same Idempotency-Key returns one order`() {
        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":1}"""),
            ).andExpect(status().isOk)

        val threads = 8
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        val successes = AtomicInteger(0)
        val orderIds = ConcurrentHashMap.newKeySet<String>()
        val otherErrors = AtomicInteger(0)
        val idemKey = "concurrent-checkout-" + System.nanoTime()
        val pool = Executors.newFixedThreadPool(threads)
        repeat(threads) {
            pool.submit {
                try {
                    start.await()
                    val result =
                        mockMvc
                            .perform(
                                post("/api/v1/orders")
                                    .header("Authorization", "Bearer $token")
                                    .header("Idempotency-Key", idemKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""{"shippingAddress":"Concurrent Test Street"}"""),
                            ).andReturn()
                    val status = result.response.status
                    if (status == 201 || status == 200) {
                        successes.incrementAndGet()
                        orderIds.add(TestJson.id(result.response.contentAsString))
                    } else {
                        otherErrors.incrementAndGet()
                        System.err.println("unexpected status=$status body=${result.response.contentAsString}")
                    }
                } catch (e: Exception) {
                    otherErrors.incrementAndGet()
                    e.printStackTrace()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(60, TimeUnit.SECONDS), "workers timed out")
        pool.shutdown()

        assertEquals(0, otherErrors.get(), "unexpected exceptions/status codes")
        assertEquals(threads, successes.get(), "all requests should succeed with the same order")
        assertEquals(1, orderIds.size, "exactly one order id for the same Idempotency-Key")
    }
}
