package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.ApiErrorDto

/**
 * HTTP / API failure with optional structured server body.
 */
class ApiException(
    val status: Int,
    message: String,
    val body: ApiErrorDto? = null,
) : Exception(message)
