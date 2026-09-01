package com.kenlikdev.qmarket.identity.dto

import java.time.Instant
import java.util.UUID

data class AdminUserResponse(
    val id: UUID,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val phone: String?,
    val enabled: Boolean,
    val emailVerified: Boolean,
    val roles: List<String>,
    val createdAt: Instant,
)

data class AdminUserPageResponse(
    val content: List<AdminUserResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
