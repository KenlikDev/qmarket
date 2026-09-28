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
class AddressIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private lateinit var token: String

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
    }

    @Test
    fun `addresses require authentication`() {
        mockMvc
            .perform(get("/api/v1/users/me/addresses"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `create list update and delete address`() {
        val createBody =
            """
            {
              "label": "Home",
              "recipientName": "Admin User",
              "phone": "+79991234567",
              "city": "Moscow",
              "streetLine1": "Red Square 1",
              "postalCode": "109012",
              "default": true
            }
            """.trimIndent()

        val createResult =
            mockMvc
                .perform(
                    post("/api/v1/users/me/addresses")
                        .header("Authorization", "Bearer $token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.city").value("Moscow"))
                .andExpect(jsonPath("$.default").value(true))
                .andReturn()

        val addressId = TestJson.id(createResult.response.contentAsString)

        mockMvc
            .perform(
                get("/api/v1/users/me/addresses")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(addressId))

        mockMvc
            .perform(
                put("/api/v1/users/me/addresses/$addressId")
                    .header("Authorization", "Bearer $token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"city":"Saint Petersburg","label":"Work"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.city").value("Saint Petersburg"))
            .andExpect(jsonPath("$.label").value("Work"))

        mockMvc
            .perform(
                delete("/api/v1/users/me/addresses/$addressId")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isNoContent)

        mockMvc
            .perform(
                get("/api/v1/users/me/addresses")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
