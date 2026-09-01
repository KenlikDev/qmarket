package com.kenlikdev.qmarket.api

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserDto(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val enabled: Boolean = true,
    val emailVerified: Boolean = false,
    val roles: List<String> = emptyList(),
    /** ISO-8601 timestamp from server. */
    val createdAt: String? = null,
)
