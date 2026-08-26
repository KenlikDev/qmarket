package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.common.security.JwtProperties
import com.kenlikdev.qmarket.common.security.JwtService
import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.LoginRequest
import com.kenlikdev.qmarket.identity.dto.RegisterRequest
import com.kenlikdev.qmarket.identity.repository.RoleRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID

class AuthServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var jwtService: JwtService
    private lateinit var jwtProperties: JwtProperties
    private lateinit var authService: AuthService

    private val userRole = Role(id = UUID.randomUUID(), name = "ROLE_USER")

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        roleRepository = mockk()
        passwordEncoder = mockk()
        jwtService = mockk()
        jwtProperties =
            JwtProperties(
                secret = "test-secret-key-that-is-long-enough-for-hs256",
                accessTokenExpirationMs = 900_000,
                refreshTokenExpirationMs = 604_800_000,
            )
        authService =
            AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                jwtService,
                jwtProperties,
                LoginRateLimiter(maxAttempts = 100, windowSeconds = 300),
            )
    }

    @Test
    fun `register creates user and returns tokens`() {
        val request =
            RegisterRequest(
                email = "user@test.com",
                password = "password123",
                firstName = "John",
            )
        val userId = UUID.randomUUID()

        every { userRepository.existsByEmail("user@test.com") } returns false
        every { roleRepository.findByName("ROLE_USER") } returns userRole
        every { passwordEncoder.encode("password123") } returns "hashed"
        every { userRepository.save(any()) } answers {
            firstArg<User>().also { it.id = userId }
        }
        every { jwtService.generateAccessToken(userId, "user@test.com", listOf("ROLE_USER")) } returns "access"
        every { jwtService.generateRefreshToken(userId) } returns "refresh"

        val result = authService.register(request)

        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertEquals("user@test.com", result.user.email)
        assertEquals(listOf("ROLE_USER"), result.user.roles)
        verify(exactly = 1) { userRepository.save(any()) }
    }

    @Test
    fun `register with existing email throws ConflictException`() {
        every { userRepository.existsByEmail("exists@test.com") } returns true

        assertThrows<ConflictException> {
            authService.register(RegisterRequest(email = "exists@test.com", password = "password123"))
        }
    }

    @Test
    fun `login with valid credentials returns tokens`() {
        val userId = UUID.randomUUID()
        val user =
            User(
                id = userId,
                email = "user@test.com",
                passwordHash = "hashed",
                enabled = true,
                roles = mutableSetOf(userRole),
            )

        every { userRepository.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("password123", "hashed") } returns true
        every { jwtService.generateAccessToken(userId, "user@test.com", listOf("ROLE_USER")) } returns "access"
        every { jwtService.generateRefreshToken(userId) } returns "refresh"

        val result = authService.login(LoginRequest(email = "user@test.com", password = "password123"))

        assertEquals("access", result.accessToken)
        assertEquals("user@test.com", result.user.email)
    }

    @Test
    fun `login with wrong password throws UnauthorizedException`() {
        val user =
            User(
                id = UUID.randomUUID(),
                email = "user@test.com",
                passwordHash = "hashed",
                enabled = true,
                roles = mutableSetOf(userRole),
            )
        every { userRepository.findByEmail("user@test.com") } returns user
        every { passwordEncoder.matches("wrong", "hashed") } returns false

        assertThrows<UnauthorizedException> {
            authService.login(LoginRequest(email = "user@test.com", password = "wrong"))
        }
    }

    @Test
    fun `login with disabled account throws UnauthorizedException`() {
        val user =
            User(
                id = UUID.randomUUID(),
                email = "user@test.com",
                passwordHash = "hashed",
                enabled = false,
                roles = mutableSetOf(userRole),
            )
        every { userRepository.findByEmail("user@test.com") } returns user

        assertThrows<UnauthorizedException> {
            authService.login(LoginRequest(email = "user@test.com", password = "password123"))
        }
    }

    @Test
    fun `login with unknown email throws UnauthorizedException`() {
        every { userRepository.findByEmail("unknown@test.com") } returns null

        assertThrows<UnauthorizedException> {
            authService.login(LoginRequest(email = "unknown@test.com", password = "password123"))
        }
    }
}
