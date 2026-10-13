package com.kenlikdev.qmarket.identity.web

import com.kenlikdev.qmarket.common.exception.ForbiddenException
import com.kenlikdev.qmarket.identity.dto.AuthResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Component
import org.springframework.util.PatternMatchUtils
import java.time.Duration

/**
 * Browser-only refresh-token transport. Native clients continue to receive refresh tokens in JSON.
 *
 * Cookie refresh/logout requests require both a marker header and a configured trusted Origin.
 * The cookie is never used to authenticate ordinary API requests; those still require a Bearer access token.
 */
@Component
class BrowserRefreshCookieSupport(
    @Value("\${qmarket.security.web-auth.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    allowedOriginsSetting: String,
    @Value("\${qmarket.security.web-auth.refresh-cookie.name:__Secure-qmarket_web_refresh}")
    private val cookieName: String,
    @Value("\${qmarket.security.web-auth.refresh-cookie.secure:true}")
    private val secure: Boolean,
    @Value("\${qmarket.security.web-auth.refresh-cookie.same-site:Strict}")
    sameSiteSetting: String,
    @Value("\${qmarket.security.jwt.refresh-token-expiration-ms:604800000}")
    refreshTokenExpirationMs: Long,
) {
    private val allowedOrigins =
        allowedOriginsSetting
            .split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
    private val sameSite =
        when (sameSiteSetting.trim().lowercase()) {
            "strict" -> "Strict"
            "lax" -> "Lax"
            "none" -> "None"
            else -> throw IllegalArgumentException("Browser refresh cookie SameSite must be Strict, Lax, or None")
        }
    private val refreshCookieMaxAge = Duration.ofMillis(refreshTokenExpirationMs)

    init {
        require(cookieName.isNotBlank()) { "Browser refresh cookie name must not be blank" }
        require(refreshTokenExpirationMs > 0) { "Refresh token expiration must be positive" }
        require(!cookieName.startsWith("__Secure-") || secure) {
            "A __Secure- browser refresh cookie must have Secure enabled"
        }
        require(sameSite != "None" || secure) {
            "SameSite=None browser refresh cookies must have Secure enabled"
        }
    }

    /**
     * Return false for native/body-token requests. When the browser marker is present, fail closed
     * unless the request's Origin is explicitly trusted. Wildcards are accepted only for loopback dev origins.
     */
    fun isBrowserAuthRequest(request: HttpServletRequest): Boolean {
        val marker = request.getHeader(WEB_AUTH_HEADER) ?: return false
        if (marker != WEB_AUTH_MARKER) throw ForbiddenException("Invalid browser authentication request")
        val origin = request.getHeader("Origin")?.trim()
        if (origin.isNullOrBlank() || allowedOrigins.none { matchesOrigin(it, origin) }) {
            throw ForbiddenException("Browser authentication origin is not allowed")
        }
        return true
    }

    fun readRefreshToken(request: HttpServletRequest): String? =
        request.cookies?.firstOrNull { it.name == cookieName }?.value?.ifBlank { null }

    /** Put the refresh JWT in an HttpOnly cookie and redact it from browser JSON responses. */
    fun applyToResponse(
        auth: AuthResponse,
        browserRequest: Boolean,
        response: HttpServletResponse,
    ): AuthResponse {
        if (!browserRequest) return auth
        check(auth.refreshToken.isNotBlank()) { "Authentication service returned an empty refresh token" }
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(auth.refreshToken, refreshCookieMaxAge))
        return auth.copy(refreshToken = "")
    }

    fun clearRefreshCookie(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", Duration.ZERO))
    }

    private fun buildCookie(value: String, maxAge: Duration): String =
        ResponseCookie
            .from(cookieName, value)
            .httpOnly(true)
            .secure(secure)
            .path(COOKIE_PATH)
            .sameSite(sameSite)
            .maxAge(maxAge)
            .build()
            .toString()

    private fun matchesOrigin(configured: String, actual: String): Boolean =
        configured == actual ||
            (configured in LOCAL_DEVELOPMENT_ORIGIN_PATTERNS && PatternMatchUtils.simpleMatch(configured, actual))

    private companion object {
        const val WEB_AUTH_HEADER = "X-QMarket-Web-Auth"
        const val WEB_AUTH_MARKER = "1"
        const val COOKIE_PATH = "/api/v1/auth"
        val LOCAL_DEVELOPMENT_ORIGIN_PATTERNS = setOf("http://localhost:*", "http://127.0.0.1:*")
    }
}
