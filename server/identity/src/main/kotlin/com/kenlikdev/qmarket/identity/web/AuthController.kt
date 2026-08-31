package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.identity.dto.AuthResponse
import com.kenlikdev.qmarket.identity.dto.LoginRequest
import com.kenlikdev.qmarket.identity.dto.RefreshTokenRequest
import com.kenlikdev.qmarket.identity.dto.RegisterRequest
import com.kenlikdev.qmarket.identity.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody request: RegisterRequest,
    ): AuthResponse = authService.register(request)

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
    ): AuthResponse = authService.login(request, clientKey = clientKey(httpRequest))

    private fun clientKey(httpRequest: HttpServletRequest): String {
        val forwarded =
            httpRequest
                .getHeader("X-Forwarded-For")
                ?.split(",")
                ?.firstOrNull()
                ?.trim()
        return forwarded?.takeIf { it.isNotEmpty() } ?: (httpRequest.remoteAddr ?: "unknown")
    }

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): AuthResponse = authService.refresh(request)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody request: RefreshTokenRequest,
    ) {
        authService.logout(request)
    }
}
