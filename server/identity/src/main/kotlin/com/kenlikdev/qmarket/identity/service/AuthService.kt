package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.ConflictException
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.common.security.JwtProperties
import com.kenlikdev.qmarket.common.security.JwtService
import com.kenlikdev.qmarket.common.validation.InputValidation
import com.kenlikdev.qmarket.identity.domain.User
import com.kenlikdev.qmarket.identity.dto.AuthResponse
import com.kenlikdev.qmarket.identity.dto.LoginRequest
import com.kenlikdev.qmarket.identity.dto.RefreshTokenRequest
import com.kenlikdev.qmarket.identity.dto.RegisterRequest
import com.kenlikdev.qmarket.identity.dto.UserResponse
import com.kenlikdev.qmarket.identity.repository.RoleRepository
import com.kenlikdev.qmarket.identity.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val jwtProperties: JwtProperties,
) {
    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        val email = request.email.lowercase().trim()
        if (userRepository.existsByEmail(email)) {
            throw ConflictException("User with email $email already exists")
        }

        val userRole =
            roleRepository
                .findByName("ROLE_USER") ?: throw IllegalStateException("ROLE_USER not found in database. Run Flyway migrations.")

        // Spring Security's PasswordEncoder.encode is annotated in a way that Kotlin sees String?
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
        return buildAuthResponse(saved)
    }

    fun login(request: LoginRequest): AuthResponse {
        val user =
            userRepository
                .findByEmail(request.email.lowercase().trim())
                ?: throw UnauthorizedException("Invalid email or password")

        if (!user.enabled) {
            throw UnauthorizedException("Account is disabled")
        }

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw UnauthorizedException("Invalid email or password")
        }

        return buildAuthResponse(user)
    }

    fun refresh(request: RefreshTokenRequest): AuthResponse {
        try {
            val claims = jwtService.parseClaims(request.refreshToken)
            if (!jwtService.isRefreshToken(claims)) {
                throw UnauthorizedException("Invalid refresh token")
            }
            val userId = jwtService.getUserId(claims)
            val user =
                userRepository
                    .findById(userId)
                    .orElseThrow { UnauthorizedException("User not found") }
            if (!user.enabled) {
                throw UnauthorizedException("Account is disabled")
            }
            return buildAuthResponse(user)
        } catch (ex: UnauthorizedException) {
            throw ex
        } catch (ex: Exception) {
            throw UnauthorizedException("Invalid refresh token")
        }
    }

    private fun buildAuthResponse(user: User): AuthResponse {
        val roles = user.roles.map { it.name }
        val userId = requireNotNull(user.id) { "User id must not be null after save" }
        val accessToken = jwtService.generateAccessToken(userId, user.email, roles)
        val refreshToken = jwtService.generateRefreshToken(userId)
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
