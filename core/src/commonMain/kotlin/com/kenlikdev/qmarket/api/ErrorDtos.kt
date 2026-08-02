package com.kenlikdev.qmarket.api

import kotlinx.serialization.Serializable

/** Matches server GlobalExceptionHandler JSON shape. */
@Serializable
data class ApiErrorDto(
    val timestamp: String? = null,
    val status: Int,
    val error: String? = null,
    val code: String? = null,
    val message: String? = null,
    val path: String? = null,
)
