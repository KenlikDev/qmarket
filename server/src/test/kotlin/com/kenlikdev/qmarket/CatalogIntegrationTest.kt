package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.support.TestJson
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var adminToken: String

    @BeforeEach
    fun loginAsAdmin() {
        val result =
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"email":"admin@qmarket.local","password":"admin123"}"""),
                ).andExpect(status().isOk)
                .andReturn()
        adminToken = TestJson.accessToken(result.response.contentAsString)
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
        val body = """{"name":"Test Category","slug":"test-category"}"""

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

    @Test
    fun `list products accepts price and sort filters`() {
        mockMvc
            .perform(
                get("/api/v1/products")
                    .param("minPrice", "0")
                    .param("maxPrice", "99999")
                    .param("sortBy", "price")
                    .param("sortDir", "asc"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
    }
}
