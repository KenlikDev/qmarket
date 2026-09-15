package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.BadRequestException
import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.common.security.JwtProperties
import com.kenlikdev.qmarket.common.security.JwtService
import com.kenlikdev.qmarket.common.validation.InputValidation
import com.kenlikdev.qmarket.identity.domain.RefreshToken
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.AuthResponse
import com.kenlikdev.qmarket.identity.dto.GoogleOAuthRequest
import com.kenlikdev.qmarket.identity.dto.LoginRequest
import com.kenlikdev.qmarket.identity.dto.RefreshTokenRequest
import com.kenlikdev.qmarket.identity.dto.RegisterRequest
import com.kenlikdev.qmarket.identity.dto.UserResponse
import com.kenlikdev.qmarket.identity.repository.RefreshTokenRepository
import com.kenlikdev.qmarket.identity.repository.RoleRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
    private val loginRateLimiter: LoginRateLimiter,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val googleIdTokenVerifierProvider: ObjectProvider<GoogleIdTokenVerifier>,
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val email = request.email.lowercase().trim()
        if (userRepository.existsByEmail(email)) {
            throw ConflictException("User with email $email already exists")
        }

        val userRole =
            roleRepository.findByName("ROLE_USER")
                ?: throw IllegalStateException("ROLE_USER not found in database. Run Flyway migrations.")

        val encodedPassword =
            passwordEncoder.encode(request.password)
                ?: throw IllegalStateException("Password encoding returned null")

        val user =
            User(
                email = email,
                passwordHash = encodedPassword,
            ).apply {
                firstName = InputValidation.normalizeOptionalName(request.firstName, "First name")
                lastName = InputValidation.normalizeOptionalName(request.lastName, "Last name")
                roles = mutableSetOf(userRole)
            }

        val saved = userRepository.save(user)
        return issueTokens(saved, familyId = UUID.randomUUID())
    }

    @Transactional
    fun login(
        request: LoginRequest,
        clientKey: String? = null,
    ): AuthResponse {
        val email = request.email.lowercase().trim()
        loginRateLimiter.assertAllowed(email, clientKey)

        val user =
            userRepository.findByEmail(email)
                ?: run {
                    loginRateLimiter.recordFailure(email, clientKey)
                    throw UnauthorizedException("Invalid email or password")
                }

        if (!user.enabled) {
            loginRateLimiter.recordFailure(email, clientKey)
            throw UnauthorizedException("Account is disabled")
        }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            loginRateLimiter.recordFailure(email, clientKey)
            throw UnauthorizedException("Invalid email or password")
        }

        loginRateLimiter.clear(email, clientKey)
        return issueTokens(user, familyId = UUID.randomUUID())
    }

    @Transactional
    fun refresh(request: RefreshTokenRequest): AuthResponse {
        try {
            val claims = jwtService.parseClaims(request.refreshToken)
            if (!jwtService.isRefreshToken(claims)) {
                throw UnauthorizedException("Invalid refresh token")
            }
            val userId = jwtService.getUserId(claims)
            val jti = jwtService.getJti(claims)
            val stored =
                refreshTokenRepository.findByJti(jti)
                    ?: throw UnauthorizedException("Invalid refresh token")

            if (stored.userId != userId) {
                throw UnauthorizedException("Invalid refresh token")
            }

            if (stored.isExpired) {
                stored.revoke()
                refreshTokenRepository.save(stored)
                throw UnauthorizedException("Refresh token expired")
            }

            if (stored.isRevoked) {
                refreshTokenRepository.revokeFamily(stored.familyId, Instant.now())
                throw UnauthorizedException("Refresh token reuse detected")
            }

            // Atomic consume: only one concurrent refresh may win (updated rows == 1).
            // If we lose the race, do NOT revoke the family — the winner legitimately issued R2.
            // Family revoke is reserved for true reuse: presenting an already-revoked jti (above).
            val consumed = refreshTokenRepository.revokeIfActive(stored.jti, Instant.now())
            if (consumed != 1) {
                throw UnauthorizedException("Refresh token already used")
            }

            val user =
                userRepository
                    .findById(userId)
                    .orElseThrow { UnauthorizedException("User not found") }
            if (!user.enabled) {
                throw UnauthorizedException("Account is disabled")
            }

            return issueTokens(user, familyId = stored.familyId)
        } catch (ex: UnauthorizedException) {
            throw ex
        } catch (ex: Exception) {
            throw UnauthorizedException("Invalid refresh token")
        }
    }

    /**
     * Best-effort revoke of the presented refresh token.
     * Not [Transactional]: callers must always clear the client session even if revoke fails
     * (expired JWT, missing V7 table, already-revoked jti). Catching exceptions inside a
     * class-level/@Transactional method leaves the TX rollback-only → UnexpectedRollbackException.
     * Repository [save] still runs in its own short transaction.
     */
    fun logout(request: RefreshTokenRequest) {
        try {
            val claims = jwtService.parseClaims(request.refreshToken)
            if (!jwtService.isRefreshToken(claims)) {
                return
            }
            val jti = jwtService.getJti(claims)
            val stored = refreshTokenRepository.findByJti(jti) ?: return
            if (!stored.isRevoked) {
                stored.revoke()
                refreshTokenRepository.save(stored)
            }
        } catch (_: Exception) {
            // best-effort: logout must not fail the client session clear
        }
    }

    @Transactional
    fun loginWithGoogle(request: GoogleOAuthRequest): AuthResponse {
        val verifier =
            googleIdTokenVerifierProvider.getIfAvailable()
                ?: throw BadRequestException("Google OAuth is not enabled")
        val claims = verifier.verify(request.idToken)
        val email = claims.email.lowercase().trim()
        var user = userRepository.findByEmail(email)
        if (user == null) {
            val userRole =
                roleRepository.findByName("ROLE_USER")
                    ?: throw IllegalStateException("ROLE_USER not found in database. Run Flyway migrations.")
            val unusablePassword =
                passwordEncoder.encode("oauth-google-" + UUID.randomUUID())
                    ?: throw IllegalStateException("Password encoding returned null")
            user =
                User(
                    email = email,
                    passwordHash = unusablePassword,
                ).apply {
                    firstName = claims.givenName
                    lastName = claims.familyName
                    emailVerified = claims.emailVerified
                    roles.add(userRole)
                }
            user = userRepository.save(user)
        } else {
            if (!user.enabled) {
                throw UnauthorizedException("User account is disabled")
            }
            var dirty = false
            if (user.firstName.isNullOrBlank() && !claims.givenName.isNullOrBlank()) {
                user.firstName = claims.givenName
                dirty = true
            }
            if (user.lastName.isNullOrBlank() && !claims.familyName.isNullOrBlank()) {
                user.lastName = claims.familyName
                dirty = true
            }
            if (claims.emailVerified && !user.emailVerified) {
                user.emailVerified = true
                dirty = true
            }
            if (dirty) {
                user = userRepository.save(user)
            }
        }
        return issueTokens(user, familyId = UUID.randomUUID())
    }

    private fun issueTokens(
        user: User,
        familyId: UUID,
    ): AuthResponse {
        val roles = user.roles.map { it.name }.toList()
        val userId = requireNotNull(user.id) { "User id must not be null after save" }
        val accessToken = jwtService.generateAccessToken(userId, user.email, roles)
        val jti = UUID.randomUUID()
        val refreshToken = jwtService.generateRefreshToken(userId, jti)
        val expiresAt = Instant.ofEpochMilli(jwtService.refreshTokenExpiresAt().time)

        refreshTokenRepository.save(
            RefreshToken(
                userId = userId,
                jti = jti,
                familyId = familyId,
                expiresAt = expiresAt,
            ),
        )

        return AuthResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = jwtProperties.accessTokenExpirationMs / 1000,
            user =
                UserResponse(
                    id = userId,
                    email = user.email,
                    firstName = user.firstName,
                    lastName = user.lastName,
                    roles = roles,
                ),
        )
    }
}
