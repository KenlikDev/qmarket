package com.kenlikdev.qmarket.api

import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class RefreshTokenRequestDto(
    val refreshToken: String,
)

@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val user: UserDto,
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val roles: List<String> = emptyList(),
)

@Serializable
data class ProfileDto(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val emailVerified: Boolean = false,
    val roles: List<String> = emptyList(),
)

@Serializable
data class UpdateProfileRequestDto(
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
)

@Serializable
data class ChangePasswordRequestDto(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class GoogleOAuthRequestDto(
    val idToken: String,
)
