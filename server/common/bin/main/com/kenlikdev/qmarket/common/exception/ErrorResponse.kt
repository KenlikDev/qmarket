package com.kenlikdev.qmarket.common.exception

import java.time.Instant

data class ErrorResponse(
    val timestamp: Instant = Instant.now(),
    val status: Int,
    val error: String,
    val code: String,
    val message: String,
    val path: String? = null,
    val details: List<FieldErrorDetail>? = null,
)

data class FieldErrorDetail(
    val field: String,
    val message: String,
    val rejectedValue: Any? = null,
)
