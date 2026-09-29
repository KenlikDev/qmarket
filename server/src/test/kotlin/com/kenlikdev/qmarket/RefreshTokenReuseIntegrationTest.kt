package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.support.TestJson
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
class RefreshTokenReuseIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun refreshTokenReuseRevokesWholeFamily() {
        val register =
            mockMvc.perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"reuse-${System.nanoTime()}@test.local","password":"password123"}"""),
            ).andExpect(status().isCreated).andReturn()

        val firstRefresh = extractRefreshToken(register.response.contentAsString)

        val rotated =
            mockMvc.perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"$firstRefresh"}"""),
            ).andReturn()
        org.junit.jupiter.api.Assertions.assertEquals(
            200,
            rotated.response.status,
            "initial refresh failed: " + rotated.response.contentAsString,
        )

        val siblingRefresh = extractRefreshToken(rotated.response.contentAsString)

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

    private fun extractRefreshToken(json: String): String =
        TestJson.parse(json).path("refreshToken").asString(null)
            ?: error("No refreshToken in response")
}
