package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.common.security.JwtProperties
import com.kenlikdev.qmarket.common.security.JwtService
import com.kenlikdev.qmarket.identity.domain.RefreshToken
import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.LoginRequest
import com.kenlikdev.qmarket.identity.dto.RefreshTokenRequest
import com.kenlikdev.qmarket.identity.dto.RegisterRequest
import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import com.kenlikdev.qmarket.identity.repository.RoleRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import io.jsonwebtoken.Claims
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.Date
import java.util.Optional
import java.util.UUID

/**
 * Unit tests: all collaborators mocked.
 *
 * [RefreshTokenRepository.save] / [UserRepository.save] use `returnsArgument 0` —
 * relaxed mocks return Object and Kotlin casts JpaRepository.save result → ClassCastException.
 */
class AuthServiceTest {
    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var passwordEncoder: PasswordEncoder
    private lateinit var refreshTokenRepository: RefreshTokenRepository
    private lateinit var jwtService: JwtService
    private lateinit var jwtProperties: JwtProperties
    private lateinit var authService: AuthService

    private val userRole = Role(id = UUID.randomUUID(), name = "ROLE_USER")

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        roleRepository = mockk()
        passwordEncoder = mockk()
        refreshTokenRepository = mockk()
        jwtService = mockk()

        every { userRepository.save(any()) } returnsArgument 0
        every { refreshTokenRepository.save(any()) } returnsArgument 0
        every { refreshTokenRepository.findByJti(any()) } returns null
        every { refreshTokenRepository.revokeFamily(any(), any()) } returns 0

        every { passwordEncoder.encode(any()) } returns "hashed"
        every { passwordEncoder.matches(any(), any()) } returns true

        // Avoid bare any() on Collection — use match { true }
        every {
            jwtService.generateAccessToken(any(), any(), match { true })
        } returns "access"
        every { jwtService.generateRefreshToken(any(), any()) } returns "refresh"
        every { jwtService.refreshTokenExpiresAt(any()) } returns Date(System.currentTimeMillis() + 86_400_000)
        every { jwtService.refreshTokenExpiresAt() } returns Date(System.currentTimeMillis() + 86_400_000)

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
                refreshTokenRepository,
            )
    }

    @Test
    fun `register creates user and returns tokens`() {
        val userId = UUID.randomUUID()
        every { userRepository.existsByEmail("user@test.com") } returns false
        every { roleRepository.findByName("ROLE_USER") } returns userRole
        every { userRepository.save(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (invocation.args[0] as User).also { it.id = userId }
        }

        val result =
            authService.register(
                RegisterRequest(email = "user@test.com", password = "password123", firstName = "John"),
            )

        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertEquals("user@test.com", result.user.email)
        assertEquals(listOf("ROLE_USER"), result.user.roles)
        verify(exactly = 1) { userRepository.save(any()) }
        verify(exactly = 1) { refreshTokenRepository.save(any()) }
        verify(exactly = 1) {
            jwtService.generateAccessToken(userId, "user@test.com", match { true })
        }
        verify(exactly = 1) { jwtService.generateRefreshToken(userId, any()) }
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

        val result = authService.login(LoginRequest(email = "user@test.com", password = "password123"))

        assertEquals("access", result.accessToken)
        assertEquals("user@test.com", result.user.email)
        verify(exactly = 1) { refreshTokenRepository.save(any()) }
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
        every { passwordEncoder.matches(any(), any()) } returns false
        assertThrows<UnauthorizedException> {
            authService.login(LoginRequest(email = "user@test.com", password = "wrong"))
        }
    }

    @Test
    fun `login with unknown email throws UnauthorizedException`() {
        every { userRepository.findByEmail("unknown@test.com") } returns null
        assertThrows<UnauthorizedException> {
            authService.login(LoginRequest(email = "unknown@test.com", password = "password123"))
        }
    }

    @Test
    fun `refresh rotates token and revokes previous jti`() {
        val userId = UUID.randomUUID()
        val jti = UUID.randomUUID()
        val familyId = UUID.randomUUID()
        val user =
            User(id = userId, email = "user@test.com", passwordHash = "hashed").apply {
                roles = mutableSetOf(userRole)
            }
        val stored =
            RefreshToken(
                userId = userId,
                jti = jti,
                familyId = familyId,
                expiresAt = Instant.now().plusSeconds(3600),
            )
        val claims = mockk<Claims>()
        every { jwtService.parseClaims("old-refresh") } returns claims
        every { jwtService.isRefreshToken(claims) } returns true
        every { jwtService.getUserId(claims) } returns userId
        every { jwtService.getJti(claims) } returns jti
        every { refreshTokenRepository.findByJti(jti) } returns stored
        every { userRepository.findById(userId) } returns Optional.of(user)

        val result = authService.refresh(RefreshTokenRequest(refreshToken = "old-refresh"))

        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertTrue(stored.isRevoked)
        verify(atLeast = 1) { refreshTokenRepository.save(any()) }
    }

    @Test
    fun `refresh of already revoked token revokes family`() {
        val userId = UUID.randomUUID()
        val jti = UUID.randomUUID()
        val familyId = UUID.randomUUID()
        val stored =
            RefreshToken(
                userId = userId,
                jti = jti,
                familyId = familyId,
                expiresAt = Instant.now().plusSeconds(3600),
                revokedAt = Instant.now(),
            )
        val claims = mockk<Claims>()
        every { jwtService.parseClaims("stolen") } returns claims
        every { jwtService.isRefreshToken(claims) } returns true
        every { jwtService.getUserId(claims) } returns userId
        every { jwtService.getJti(claims) } returns jti
        every { refreshTokenRepository.findByJti(jti) } returns stored
        every { refreshTokenRepository.revokeFamily(familyId, any()) } returns 2

        assertThrows<UnauthorizedException> {
            authService.refresh(RefreshTokenRequest(refreshToken = "stolen"))
        }
        verify(exactly = 1) { refreshTokenRepository.revokeFamily(familyId, any()) }
    }

    @Test
    fun `logout is best-effort on invalid token`() {
        every { jwtService.parseClaims(any()) } throws RuntimeException("bad token")
        authService.logout(RefreshTokenRequest(refreshToken = "not-a-jwt"))
        // must not throw UnexpectedRollbackException / any exception
    }

    @Test
    fun `logout revokes stored refresh token`() {
        val jti = UUID.randomUUID()
        val stored =
            RefreshToken(
                userId = UUID.randomUUID(),
                jti = jti,
                familyId = UUID.randomUUID(),
                expiresAt = Instant.now().plusSeconds(3600),
            )
        val claims = mockk<Claims>()
        every { jwtService.parseClaims("refresh") } returns claims
        every { jwtService.isRefreshToken(claims) } returns true
        every { jwtService.getJti(claims) } returns jti
        every { refreshTokenRepository.findByJti(jti) } returns stored

        authService.logout(RefreshTokenRequest(refreshToken = "refresh"))

        assertTrue(stored.isRevoked)
        verify(exactly = 1) { refreshTokenRepository.save(stored) }
    }
}
