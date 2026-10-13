package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.dto.AuthResponse
import com.kenlikdev.qmarket.identity.dto.GoogleOAuthRequest
import com.kenlikdev.qmarket.identity.dto.LoginRequest
import com.kenlikdev.qmarket.identity.dto.RefreshTokenRequest
import com.kenlikdev.qmarket.identity.dto.RegisterRequest
import com.kenlikdev.qmarket.identity.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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
    private val browserRefreshCookies: BrowserRefreshCookieSupport,
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody request: RegisterRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthResponse {
        val browserRequest = browserRefreshCookies.isBrowserAuthRequest(httpRequest)
        return browserRefreshCookies.applyToResponse(authService.register(request), browserRequest, httpResponse)
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthResponse {
        val browserRequest = browserRefreshCookies.isBrowserAuthRequest(httpRequest)
        val auth = authService.login(request, clientKey = clientKey(httpRequest))
        return browserRefreshCookies.applyToResponse(auth, browserRequest, httpResponse)
    }

    /**
     * Forwarded headers are not trusted here because this service does not own the proxy trust boundary.
     */
    private fun clientKey(httpRequest: HttpServletRequest): String = httpRequest.remoteAddr ?: "unknown"

    @PostMapping("/refresh")
    fun refresh(
        @Valid @RequestBody(required = false) request: RefreshTokenRequest?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthResponse {
        val browserRequest = browserRefreshCookies.isBrowserAuthRequest(httpRequest)
        val refreshToken =
            if (browserRequest) {
                browserRefreshCookies.readRefreshToken(httpRequest)
            } else {
                request?.refreshToken
            }

        if (refreshToken.isNullOrBlank()) {
            if (browserRequest) browserRefreshCookies.clearRefreshCookie(httpResponse)
            throw UnauthorizedException("Invalid refresh token")
        }

        val auth =
            try {
                authService.refresh(RefreshTokenRequest(refreshToken))
            } catch (exception: UnauthorizedException) {
                if (browserRequest) browserRefreshCookies.clearRefreshCookie(httpResponse)
                throw exception
            }

        return browserRefreshCookies.applyToResponse(auth, browserRequest, httpResponse)
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(
        @Valid @RequestBody(required = false) request: RefreshTokenRequest?,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ) {
        val browserRequest = browserRefreshCookies.isBrowserAuthRequest(httpRequest)
        val refreshToken =
            if (browserRequest) {
                browserRefreshCookies.readRefreshToken(httpRequest)
            } else {
                request?.refreshToken
            }

        if (!browserRequest && refreshToken.isNullOrBlank()) {
            throw UnauthorizedException("Refresh token is required")
        }
        if (!refreshToken.isNullOrBlank()) {
            authService.logout(RefreshTokenRequest(refreshToken))
        }
        if (browserRequest) browserRefreshCookies.clearRefreshCookie(httpResponse)
    }

    @PostMapping("/oauth/google")
    fun loginWithGoogle(
        @Valid @RequestBody request: GoogleOAuthRequest,
        httpRequest: HttpServletRequest,
        httpResponse: HttpServletResponse,
    ): AuthResponse {
        val browserRequest = browserRefreshCookies.isBrowserAuthRequest(httpRequest)
        return browserRefreshCookies.applyToResponse(
            authService.loginWithGoogle(request),
            browserRequest,
            httpResponse,
        )
    }
}
