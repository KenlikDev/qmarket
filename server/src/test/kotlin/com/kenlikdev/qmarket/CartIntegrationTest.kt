package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.support.TestJson
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CartIntegrationTest {
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

        val productsResult =
            mockMvc
                .perform(get("/api/v1/products"))
                .andExpect(status().isOk)
                .andReturn()
        productId = TestJson.firstContentId(productsResult.response.contentAsString)

        mockMvc.perform(
            delete("/api/v1/cart")
                .header("Authorization", "Bearer $token"),
        )
    }

    @Test
    fun `cart requires authentication`() {
        mockMvc
            .perform(get("/api/v1/cart"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `add update remove and clear cart`() {
        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":2}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(2))
            .andExpect(jsonPath("$.items[0].productId").value(productId))

        mockMvc
            .perform(
                put("/api/v1/cart/items/$productId")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"quantity":3}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(3))

        mockMvc
            .perform(
                get("/api/v1/cart")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(3))

        mockMvc
            .perform(
                delete("/api/v1/cart/items/$productId")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(0))

        mockMvc
            .perform(
                post("/api/v1/cart/items")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"productId":"$productId","quantity":1}"""),
            ).andExpect(status().isOk)

        mockMvc
            .perform(
                delete("/api/v1/cart")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.totalItems").value(0))
    }
}
