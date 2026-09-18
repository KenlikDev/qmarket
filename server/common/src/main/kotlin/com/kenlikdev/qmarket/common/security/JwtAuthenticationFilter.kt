package com.kenlikdev.qmarket.common.security

import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(HttpHeaders.AUTHORIZATION)
        if (header.isNullOrBlank() || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        val token = header.substring(7)
        try {
            val claims = jwtService.parseClaims(token)
            if (jwtService.isAccessToken(claims) && SecurityContextHolder.getContext().authentication == null) {
                val userId = jwtService.getUserId(claims)

                val roles =
                    (claims["roles"] as? Collection<*>)
                        ?.filterIsInstance<String>()
                        .orEmpty()
                val authorities = roles.map(::SimpleGrantedAuthority)
                val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (_: JwtException) {
            // Invalid token — leave context empty; security will reject if endpoint requires auth.
        } catch (_: IllegalArgumentException) {
            // Malformed user id/claim — leave context empty and let Spring Security reject if required.
        }

        filterChain.doFilter(request, response)
    }
}
