package com.kenlikdev.qmarket.common.exception

import jakarta.persistence.OptimisticLockException
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

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
                        )

                    else ->
                        FieldErrorDetail(
                            field = error.objectName,
                            message = error.defaultMessage ?: "Invalid value",
                        )
                }
            }

        return badRequest(
            code = "VALIDATION_ERROR",
            message = "Validation failed",
            path = request.requestURI,
            details = details,
        )
    }

    /**
     * Spring MVC uses method-level validation for constrained controller parameters.
     * Both object validation and executable-parameter validation must be mapped to
     * the API's stable 400 response shape.
     */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidation(
        ex: HandlerMethodValidationException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        badRequest(
            code = "VALIDATION_ERROR",
            message = "Validation failed",
            path = request.requestURI,
            details = ex.parameterValidationResults.flatMap { result ->
                result.resolvableErrors.map { error ->
                    FieldErrorDetail(
                        field = result.methodParameter.parameterName ?: "parameter",
                        message = error.defaultMessage ?: "Invalid value",
                    )
                }
            },
        )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(
        ex: ConstraintViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        badRequest(
            code = "VALIDATION_ERROR",
            message = "Validation failed",
            path = request.requestURI,
            details = ex.constraintViolations.map { violation ->
                FieldErrorDetail(
                    field = violation.propertyPath.toString(),
                    message = violation.message,
                )
            },
        )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedRequest(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        badRequest(
            code = "INVALID_REQUEST",
            message = "Request body is malformed or contains invalid values",
            path = request.requestURI,
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(
        ex: MethodArgumentTypeMismatchException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        badRequest(
            code = "INVALID_REQUEST",
            message = "Request parameter '${ex.name}' has an invalid value",
            path = request.requestURI,
        )

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> =
        badRequest(
            code = "INVALID_REQUEST",
            message = "Required request parameter '${ex.parameterName}' is missing",
            path = request.requestURI,
        )

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

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(
        ex: DataIntegrityViolationException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        log.warn("Data integrity violation: {}", ex.mostSpecificCause.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ErrorResponse(
                status = HttpStatus.CONFLICT.value(),
                error = HttpStatus.CONFLICT.reasonPhrase,
                code = "DATA_CONFLICT",
                message = "The request conflicts with existing data",
                path = request.requestURI,
            ),
        )
    }

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

    private fun badRequest(
        code: String,
        message: String,
        path: String,
        details: List<FieldErrorDetail>? = null,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(
            ErrorResponse(
                status = HttpStatus.BAD_REQUEST.value(),
                error = HttpStatus.BAD_REQUEST.reasonPhrase,
                code = code,
                message = message,
                path = path,
                details = details?.takeIf { it.isNotEmpty() },
            ),
        )
}