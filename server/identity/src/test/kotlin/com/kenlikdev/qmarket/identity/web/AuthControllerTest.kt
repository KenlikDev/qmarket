package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.GlobalExceptionHandler
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.dto.AuthResponse
import com.kenlikdev.qmarket.identity.dto.UserResponse
import com.kenlikdev.qmarket.identity.service.AuthService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.util.UUID

class AuthControllerTest {
    private lateinit var mockMvc: MockMvc
    private lateinit var authService: AuthService

    private val sampleResponse =
        AuthResponse(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresIn = 900,
            user =
                UserResponse(
                    id = UUID.randomUUID(),
                    email = "user@test.com",
                    firstName = "John",
                    lastName = "Doe",
                    roles = listOf("ROLE_USER"),
                ),
        )

    @BeforeEach
    fun setUp() {
        authService = mockk()
        mockMvc =
            MockMvcBuilders
                .standaloneSetup(AuthController(authService))
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @Test
    fun `POST register returns 201 and tokens`() {
        every { authService.register(any()) } returns sampleResponse

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "email": "user@test.com",
                          "password": "password123",
                          "firstName": "John",
                          "lastName": "Doe"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.user.email").value("user@test.com"))

        verify(exactly = 1) { authService.register(any()) }
    }

    @Test
    fun `POST register with duplicate email returns 409`() {
        every { authService.register(any()) } throws ConflictException("User already exists")

        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email": "dup@test.com", "password": "password123"}"""),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("CONFLICT"))
    }

    @Test
    fun `POST register with invalid body returns 400`() {
        mockMvc
            .perform(
                post("/api/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email": "not-an-email", "password": "123"}"""),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `POST login returns 200 and tokens`() {
        every { authService.login(any(), any()) } returns sampleResponse

        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email": "user@test.com", "password": "password123"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
    }

    @Test
    fun `POST login with wrong password returns 401`() {
        every { authService.login(any(), any()) } throws UnauthorizedException("Invalid credentials")

        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email": "user@test.com", "password": "wrong"}"""),
            ).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
    }

    @Test
    fun `POST logout returns 204`() {
        every { authService.logout(any()) } returns Unit

        mockMvc
            .perform(
                post("/api/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"refresh-token"}"""),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) { authService.logout(any()) }
    }

    @Test
    fun `POST refresh returns 200 and tokens`() {
        every { authService.refresh(any()) } returns sampleResponse

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"refresh-token"}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))

        verify(exactly = 1) { authService.refresh(any()) }
    }

    @Test
    fun `POST refresh with invalid token returns 401`() {
        every { authService.refresh(any()) } throws UnauthorizedException("Invalid refresh token")

        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"refreshToken":"bad"}"""),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST login passes X-Forwarded-For as clientKey`() {
        every { authService.login(any(), clientKey = "203.0.113.10") } returns sampleResponse

        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                    .content("""{"email":"user@test.com","password":"password123"}"""),
            ).andExpect(status().isOk)

        verify(exactly = 1) { authService.login(any(), clientKey = "203.0.113.10") }
    }
}
