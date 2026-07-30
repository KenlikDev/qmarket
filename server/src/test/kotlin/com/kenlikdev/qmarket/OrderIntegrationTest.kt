package com.kenlikdev.qmarket

import org.hamcrest.Matchers.greaterThan
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
import org.testcontainers.junit.jupiter.Testcontainers

/**
 * Full-stack checkout path: auth → cart → order → list/cancel.
 * Requires Docker (Testcontainers PostgreSQL).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class OrderIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var token: String
    private lateinit var productId: String

    @BeforeEach
    fun setUp() {
        val loginBody = """{"email":"admin@qmarket.local","password":"admin123"}"""

        val loginResult =
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody),
                ).andExpect(status().isOk)
                .andReturn()

        val loginJson = loginResult.response.contentAsString
        token = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(loginJson)?.groupValues?.get(1)
            ?: error("No accessToken: $loginJson")

        val productsJson =
            mockMvc
                .perform(get("/api/v1/products"))
                .andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString
        productId = Regex("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"").find(productsJson)?.groupValues?.get(1)
            ?: error("No product id in: $productsJson")

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

        val orderJson = createResult.response.contentAsString
        val orderId =
            Regex("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"").find(orderJson)?.groupValues?.get(1)
                ?: error("No order id: $orderJson")

        // cart should be empty after checkout
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

        val orderId =
            Regex("\"id\"\\s*:\\s*\"([0-9a-fA-F-]{36})\"")
                .find(createResult.response.contentAsString)
                ?.groupValues
                ?.get(1)
                ?: error("No order id")

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
}
