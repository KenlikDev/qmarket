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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class CatalogIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var adminToken: String

    @BeforeEach
    fun loginAsAdmin() {
        val login =
            """
            {"email": "admin@qmarket.local", "password": "admin123"}
            """.trimIndent()

        val result =
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login),
                ).andExpect(status().isOk)
                .andReturn()

        val response = result.response.contentAsString
        val match = Regex("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").find(response)
        adminToken = match?.groupValues?.get(1)
            ?: throw IllegalStateException("No accessToken in login response: $response")
    }

    @Test
    fun `list products is public and returns seeded data`() {
        mockMvc
            .perform(get("/api/v1/products"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
    }

    @Test
    fun `list categories is public`() {
        mockMvc
            .perform(get("/api/v1/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$[0].slug").isNotEmpty)
    }

    @Test
    fun `create category requires admin token`() {
        val body =
            """
            {"name": "Test Category", "slug": "test-category"}
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/categories")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isForbidden)

        mockMvc
            .perform(
                post("/api/v1/categories")
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.slug").value("test-category"))
    }

    @Test
    fun `create product requires admin token`() {
        val body =
            """
            {
              "name": "Test Product",
              "slug": "test-product",
              "price": 9.99,
              "stockQuantity": 10
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/products")
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Test Product"))
            .andExpect(jsonPath("$.price").value(9.99))
    }
}
