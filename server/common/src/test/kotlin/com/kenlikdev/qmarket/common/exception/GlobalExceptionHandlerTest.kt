package com.kenlikdev.qmarket.common.exception

import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.http.HttpServletRequest
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException

class GlobalExceptionHandlerTest {
    private val handler = GlobalExceptionHandler()
    private lateinit var request: HttpServletRequest

    @BeforeEach
    fun setUp() {
        request = mockk()
        every { request.requestURI } returns "/api/v1/test"
    }

    @Test
    fun `ApiException maps to declared status and code`() {
        val ex = NotFoundException("missing")
        val response = handler.handleApiException(ex, request)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        assertEquals("NOT_FOUND", response.body?.code)
        assertEquals("missing", response.body?.message)
        assertEquals("/api/v1/test", response.body?.path)
    }

    @Test
    fun `ConflictException maps to 409`() {
        val response = handler.handleApiException(ConflictException("dup"), request)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("CONFLICT", response.body?.code)
    }

    @Test
    fun `BadCredentials maps to 401`() {
        val response = handler.handleAuth(BadCredentialsException("bad"), request)
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        assertEquals("UNAUTHORIZED", response.body?.code)
    }

    @Test
    fun `AccessDenied maps to 403`() {
        val response = handler.handleAccessDenied(AccessDeniedException("no"), request)
        assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
        assertEquals("FORBIDDEN", response.body?.code)
    }

    @Test
    fun `optimistic lock maps to 409`() {
        val ex = ObjectOptimisticLockingFailureException(String::class.java, "id")
        val response = handler.handleOptimisticLock(ex, request)
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
        assertEquals("OPTIMISTIC_LOCK", response.body?.code)
    }

    @Test
    fun `unexpected exception maps to 500 without leaking message`() {
        val response = handler.handleGeneric(RuntimeException("secret db password"), request)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        assertEquals("INTERNAL_ERROR", response.body?.code)
        assertEquals("An unexpected error occurred", response.body?.message)
    }
    @Test
    fun `validation errors do not expose rejected values`() {
        val bindingResult = BeanPropertyBindingResult(Any(), "request")
        bindingResult.addError(FieldError("request", "password", "super-secret"))
        val exception = MethodArgumentNotValidException(null, bindingResult)

        val response = handler.handleValidation(exception, request)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        assertEquals(
            FieldErrorDetail(field = "password", message = "super-secret"),
            response.body?.details?.single(),
        )
    }

}
