package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.common.security.JwtProperties
import com.kenlikdev.qmarket.common.security.JwtService
import com.kenlikdev.qmarket.identity.domain.RefreshToken
import com.kenlikdev.qmarket.identity.domain.Role
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.GoogleOAuthRequest
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
import org.springframework.beans.factory.ObjectProvider
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
    private lateinit var googleIdTokenVerifier: GoogleIdTokenVerifier
    private lateinit var googleIdTokenVerifierProvider: ObjectProvider<GoogleIdTokenVerifier>

    private val userRole = Role(id = UUID.randomUUID(), name = "ROLE_USER")

    @BeforeEach
    fun setUp() {
        userRepository = mockk()
        roleRepository = mockk()
        passwordEncoder = mockk()
        refreshTokenRepository = mockk()
        googleIdTokenVerifier = mockk()
        googleIdTokenVerifierProvider = mockk()
        every { googleIdTokenVerifierProvider.getIfAvailable() } returns googleIdTokenVerifier
        jwtService = mockk()

        every { userRepository.save(any()) } returnsArgument 0
        every { refreshTokenRepository.save(any()) } returnsArgument 0
        every { refreshTokenRepository.findByJti(any()) } returns null
        every { refreshTokenRepository.revokeFamily(any(), any()) } returns 0
        every { refreshTokenRepository.revokeIfActive(any(), any()) } returns 1
        every { userRepository.findByIdForUpdate(any()) } returns null
        every { userRepository.findByGoogleSubject(any()) } returns null

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
                googleIdTokenVerifierProvider,
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
        every { userRepository.findByIdForUpdate(userId) } returns user
        every { refreshTokenRepository.findByJti(jti) } returns stored

        val result = authService.refresh(RefreshTokenRequest(refreshToken = "old-refresh"))

        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        verify(exactly = 1) { refreshTokenRepository.revokeIfActive(jti, any()) }
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
        every { jwtService.parseClaims(any()) } throws IllegalArgumentException("bad token")
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

    @Test
    fun `concurrent refresh loser does not revoke family`() {
        val userId = UUID.randomUUID()
        val jti = UUID.randomUUID()
        val familyId = UUID.randomUUID()
        val user =
            User(
                id = userId,
                email = "user@test.com",
                passwordHash = "hash",
            ).apply { roles.add(userRole) }
        val stored =
            RefreshToken(
                userId = userId,
                jti = jti,
                familyId = familyId,
                expiresAt = Instant.now().plusSeconds(3600),
            )
        every { jwtService.parseClaims(any()) } returns mockk(relaxed = true)
        every { jwtService.isRefreshToken(any()) } returns true
        every { jwtService.getUserId(any()) } returns userId
        every { jwtService.getJti(any()) } returns jti
        every { refreshTokenRepository.findByJti(jti) } returns stored
        every { refreshTokenRepository.revokeIfActive(jti, any()) } returns 0

        assertThrows<UnauthorizedException> {
            authService.refresh(RefreshTokenRequest(refreshToken = "old-refresh"))
        }
        verify(exactly = 0) { refreshTokenRepository.revokeFamily(any(), any()) }
    }

    @Test
    fun `loginWithGoogle creates user when email is new`() {
        val userId = UUID.randomUUID()
        every { googleIdTokenVerifier.verify("id-token") } returns
            GoogleIdTokenClaims(
                subject = "google-sub",
                email = "google.user@gmail.com",
                emailVerified = true,
                givenName = "Google",
                familyName = "User",
            )
        every { userRepository.findByGoogleSubject("google-sub") } returns null
        every { userRepository.findByEmail("google.user@gmail.com") } returns null
        every { roleRepository.findByName("ROLE_USER") } returns userRole
        every { userRepository.save(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            (invocation.args[0] as User).also { it.id = userId }
        }

        val result = authService.loginWithGoogle(GoogleOAuthRequest(idToken = "id-token"))

        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertEquals("google.user@gmail.com", result.user.email)
        verify { userRepository.save(match { it.googleSubject == "google-sub" }) }
    }

    @Test
    fun `loginWithGoogle links existing email user to Google subject`() {
        val userId = UUID.randomUUID()
        val existing =
            User(
                id = userId,
                email = "google.user@gmail.com",
                passwordHash = "hashed",
                enabled = true,
            ).apply { roles.add(userRole) }
        every { userRepository.findByGoogleSubject("google-sub") } returns null
        every { userRepository.findByEmail("google.user@gmail.com") } returns existing

        every { googleIdTokenVerifier.verify("id-token") } returns
            GoogleIdTokenClaims(
                subject = "google-sub",
                email = "google.user@gmail.com",
                emailVerified = true,
            )

        val result = authService.loginWithGoogle(GoogleOAuthRequest(idToken = "id-token"))

        assertEquals(userId, result.user.id)
        verify { userRepository.save(match { it.id == userId && it.googleSubject == "google-sub" }) }
    }

    @Test
    fun `loginWithGoogle rejects unverified email when linking existing account`() {
        val existing =
            User(
                id = UUID.randomUUID(),
                email = "google.user@gmail.com",
                passwordHash = "hashed",
                enabled = true,
            ).apply { roles.add(userRole) }
        every { userRepository.findByGoogleSubject("google-sub") } returns null
        every { userRepository.findByEmail("google.user@gmail.com") } returns existing
        every { googleIdTokenVerifier.verify("id-token") } returns
            GoogleIdTokenClaims(
                subject = "google-sub",
                email = "google.user@gmail.com",
                emailVerified = false,
            )

        assertThrows<UnauthorizedException> {
            authService.loginWithGoogle(GoogleOAuthRequest(idToken = "id-token"))
        }
    }

    @Test
    fun `loginWithGoogle rejects a different subject for an already linked account`() {
        val existing =
            User(
                id = UUID.randomUUID(),
                email = "google.user@gmail.com",
                passwordHash = "hashed",
                enabled = true,
                googleSubject = "existing-subject",
            ).apply { roles.add(userRole) }
        every { userRepository.findByGoogleSubject("new-subject") } returns null
        every { userRepository.findByEmail("google.user@gmail.com") } returns existing
        every { googleIdTokenVerifier.verify("id-token") } returns
            GoogleIdTokenClaims(
                subject = "new-subject",
                email = "google.user@gmail.com",
                emailVerified = true,
            )

        assertThrows<ConflictException> {
            authService.loginWithGoogle(GoogleOAuthRequest(idToken = "id-token"))
        }
    }

    @Test
    fun `loginWithGoogle reuses existing user by stable subject`() {
        val userId = UUID.randomUUID()
        val existing =
            User(
                id = userId,
                email = "google.user@gmail.com",
                passwordHash = "hashed",
                firstName = "Existing",
                enabled = true,
                emailVerified = true,
            ).apply { roles.add(userRole) }
        every { googleIdTokenVerifier.verify("id-token") } returns
            GoogleIdTokenClaims(
                subject = "google-sub",
                email = "google.user@gmail.com",
                emailVerified = true,
            )
        every { userRepository.findByGoogleSubject("google-sub") } returns existing

        val result = authService.loginWithGoogle(GoogleOAuthRequest(idToken = "id-token"))

        assertEquals(userId, result.user.id)
        assertEquals("google.user@gmail.com", result.user.email)
    }

    @Test
    fun `loginWithGoogle when oauth disabled throws`() {
        every { googleIdTokenVerifierProvider.getIfAvailable() } returns null
        assertThrows<BadRequestException> {
            authService.loginWithGoogle(GoogleOAuthRequest(idToken = "id-token"))
        }
    }

    @Test
    fun `refresh propagates repository infrastructure failures`() {
        val claims = mockk<Claims>()
        every { jwtService.parseClaims("refresh") } returns claims
        every { jwtService.isRefreshToken(claims) } returns true
        every { jwtService.getUserId(claims) } returns UUID.randomUUID()
        every { jwtService.getJti(claims) } returns UUID.randomUUID()
        every { refreshTokenRepository.findByJti(any()) } throws IllegalStateException("database unavailable")

        assertThrows<IllegalStateException> {
            authService.refresh(RefreshTokenRequest(refreshToken = "refresh"))
        }
    }
}
