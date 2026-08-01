package com.kenlikdev.qmarket.common.exception

import org.springframework.http.HttpStatus

open class ApiException(
    val status: HttpStatus,
    override val message: String,
    val code: String = status.name,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class NotFoundException(
    message: String = "Resource not found",
) : ApiException(HttpStatus.NOT_FOUND, message, "NOT_FOUND")

class ConflictException(
    message: String,
) : ApiException(HttpStatus.CONFLICT, message, "CONFLICT")

class BadRequestException(
    message: String,
) : ApiException(HttpStatus.BAD_REQUEST, message, "BAD_REQUEST")

class UnauthorizedException(
    message: String = "Unauthorized",
) : ApiException(HttpStatus.UNAUTHORIZED, message, "UNAUTHORIZED")

class ForbiddenException(
    message: String = "Forbidden",
) : ApiException(HttpStatus.FORBIDDEN, message, "FORBIDDEN")
