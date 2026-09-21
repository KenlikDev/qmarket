package com.kenlikdev.qmarket.identity.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

data class RegisterRequest(
    @field:NotBlank @field:Email
    val email: String,
    @field:NotBlank @field:Size(min = 8, max = 100)
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
)

data class LoginRequest(
    @field:NotBlank @field:Email
    val email: String,
    @field:NotBlank
    val password: String,
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserResponse,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val roles: List<String>,
)

data class RefreshTokenRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class ProfileResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val phone: String?,
    val emailVerified: Boolean,
    val roles: List<String>,
)

data class UpdateProfileRequest(
    @field:Size(max = 100)
    val firstName: String? = null,
    @field:Size(max = 100)
    val lastName: String? = null,
    @field:Size(max = 30)
    val phone: String? = null,
)

data class ChangePasswordRequest(
    @field:NotBlank
    val currentPassword: String,
    @field:NotBlank
    @field:Size(min = 8, max = 100)
    val newPassword: String,
)

data class GoogleOAuthRequest(
    @field:NotBlank @field:Size(max = 8192)
    val idToken: String,
)
