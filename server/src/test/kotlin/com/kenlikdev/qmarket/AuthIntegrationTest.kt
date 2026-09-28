package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.support.TestJson

import com.kenlikdev.qmarket.support.TestJson

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Auth API against Testcontainers PostgreSQL (jdbc:tc URL in application-test.yml).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `register new user returns tokens`() {
        val body =
            """
            {
              "email": "newuser@test.local",
              "password": "password123",
              "firstName": "New",
              "lastName": "User"
            }
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("newuser@test.local"))
            .andExpect(jsonPath("$.user.roles[0]").value("ROLE_USER"))
    }

    @Test
    fun `login with valid credentials returns tokens`() {
        val register =
            """
            {"email": "login@test.local", "password": "password123"}
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(register),
            ).andExpect(status().isCreated)

        val login =
            """
            {"email": "login@test.local", "password": "password123"}
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(login),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.user.email").value("login@test.local"))
    }

    @Test
    fun `login with wrong password returns 401`() {
        val login =
            """
            {"email": "admin@qmarket.local", "password": "wrongpassword"}
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(login),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `register with duplicate email returns 409`() {
        val body =
            """
            {"email": "dup@test.local", "password": "password123"}
            """.trimIndent()

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isCreated)

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body),
            ).andExpect(status().isConflict)
    }

    @Test
    fun `refresh token reuse durably revokes the whole family`() {
        val register =
            """
            {
              "email": "refresh-reuse@test.local",
              "password": "password123"
            }
            """.trimIndent()

        val first =
            mockMvc
                .perform(
                    post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(register),
                ).andExpect(status().isCreated)
                .andReturn()
        val firstRefresh = TestJson.refreshToken(first.response.contentAsString)

        val rotated =
            mockMvc
                .perform(
                    post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"refreshToken":"$firstRefresh"}"""),
                ).andExpect(status().isOk)
                .andReturn()
        val siblingRefresh = TestJson.refreshToken(rotated.response.contentAsString)

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"$firstRefresh"}"""),
            ).andExpect(status().isUnauthorized)

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"$siblingRefresh"}"""),
            ).andExpect(status().isUnauthorized)
    }
}
