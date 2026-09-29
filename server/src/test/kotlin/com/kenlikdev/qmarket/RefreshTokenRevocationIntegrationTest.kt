package com.kenlikdev.qmarket

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenRevocationIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `reusing a consumed refresh token durably revokes the token family`() {
        val email = "refresh-reuse-${System.nanoTime()}@test.local"
        val registered =
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","password":"password123"}"""),
            ).andExpect(status().isCreated).andReturn()

        val first = objectMapper.readTree(registered.response.contentAsString)
        val firstRefresh = first.path("refreshToken").textValue()
        assertNotNull(firstRefresh)

        val rotated =
            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"$firstRefresh"}"""),
            ).andExpect(status().isOk).andReturn()

        val second = objectMapper.readTree(rotated.response.contentAsString)
        val siblingRefresh = second.path("refreshToken").textValue()
        assertNotNull(siblingRefresh)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$firstRefresh"}"""),
        ).andExpect(status().isUnauthorized)

        mockMvc.perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$siblingRefresh"}"""),
        ).andExpect(status().isUnauthorized)
    }
}
