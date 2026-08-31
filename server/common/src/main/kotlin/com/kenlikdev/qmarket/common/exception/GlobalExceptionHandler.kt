package com.kenlikdev.qmarket.common.exception

import jakarta.persistence.OptimisticLockException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
open class GlobalExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(
        ex: ApiException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("API exception: {} - {}", ex.code, ex.message)
        return ResponseEntity.status(ex.status).body(
            ErrorResponse(
                status = ex.status.value(),
                error = ex.status.reasonPhrase,
                code = ex.code,
                message = ex.message,
                path = request.requestURI,
            ),
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        val details =
            ex.bindingResult.allErrors.map { error ->
                when (error) {
                    is FieldError ->
                        FieldErrorDetail(
                            field = error.field,
                            message = error.defaultMessage ?: "Invalid value",
                            rejectedValue = error.rejectedValue,
                        )
                    else ->
                        FieldErrorDetail(
                            field = error.objectName,
                            message = error.defaultMessage ?: "Invalid value",
                        )
                }
            }
        return ResponseEntity.badRequest().body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                code = "VALIDATION_ERROR",
                message = "Validation failed",
                path = request.requestURI,
                details = details,
            ),
        )
    }

    @ExceptionHandler(BadCredentialsException::class, AuthenticationException::class)
    fun handleAuth(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
            ErrorResponse(
                status = HttpStatus.UNAUTHORIZED.value(),
                error = HttpStatus.UNAUTHORIZED.reasonPhrase,
                code = "UNAUTHORIZED",
                message = "Invalid credentials",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(
        ex: AccessDeniedException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(
            ErrorResponse(
                status = HttpStatus.FORBIDDEN.value(),
                error = HttpStatus.FORBIDDEN.reasonPhrase,
                code = "FORBIDDEN",
                message = "Access denied",
                path = request.requestURI,
            ),
        )

    @ExceptionHandler(OptimisticLockException::class, ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("Optimistic lock conflict: {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = "Conflict",
                code = "OPTIMISTIC_LOCK",
                message = "Resource was modified concurrently; retry the operation",
                path = request.requestURI,
            ),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(
        ex: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
            ErrorResponse(
                status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
                error = HttpStatus.INTERNAL_SERVER_ERROR.reasonPhrase,
                code = "INTERNAL_ERROR",
                message = "An unexpected error occurred",
                path = request.requestURI,
            ),
        )
    }
}
