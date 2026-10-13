package com.kenlikdev.qmarket

import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.UUID

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
    fun `browser refresh token stays in a cookie across rotation and logout`() {
        val email = "browser-session-${UUID.randomUUID()}@test.local"
        val credentials = """{"email":"$email","password":"password123"}"""

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(credentials),
            ).andExpect(status().isCreated)

        val loginResponse =
            mockMvc
                .perform(
                    post("/api/v1/auth/login")
                        .header("X-QMarket-Web-Auth", "1")
                        .header("Origin", "http://localhost:8080")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.refreshToken").value(""))
                .andReturn()
                .response

        val cookieName = "__Secure-qmarket_web_refresh"
        val loginSetCookie = requireNotNull(loginResponse.getHeader("Set-Cookie")) as String
        assertTrue(loginSetCookie.contains("HttpOnly"))
        assertTrue(loginSetCookie.contains("Secure"))
        assertTrue(loginSetCookie.contains("SameSite=Strict"))
        assertTrue(loginSetCookie.contains("Path=/api/v1/auth"))
        val loginCookieValue = loginSetCookie.substringAfter("$cookieName=").substringBefore(";")
        assertTrue(loginCookieValue.isNotBlank())

        val refreshResponse =
            mockMvc
                .perform(
                    post("/api/v1/auth/refresh")
                        .header("X-QMarket-Web-Auth", "1")
                        .header("Origin", "http://localhost:8080")
                        .cookie(Cookie(cookieName, loginCookieValue)),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.refreshToken").value(""))
                .andReturn()
                .response

        val refreshSetCookie = requireNotNull(refreshResponse.getHeader("Set-Cookie")) as String
        val rotatedCookieValue = refreshSetCookie.substringAfter("$cookieName=").substringBefore(";")
        assertNotEquals(loginCookieValue, rotatedCookieValue)

        val logoutResponse =
            mockMvc
                .perform(
                    post("/api/v1/auth/logout")
                        .header("X-QMarket-Web-Auth", "1")
                        .header("Origin", "http://localhost:8080")
                        .cookie(Cookie(cookieName, rotatedCookieValue)),
                ).andExpect(status().isNoContent)
                .andReturn()
                .response

        val clearCookie = requireNotNull(logoutResponse.getHeader("Set-Cookie")) as String
        assertTrue(clearCookie.contains("Max-Age=0"))
        assertTrue(clearCookie.contains("HttpOnly"))
    }
}
