package com.kenlikdev.qmarket.common.security

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

                @Suppress("UNCHECKED_CAST")
                val roles = (claims["roles"] as? Collection<String>).orEmpty()
                val authorities = roles.map { SimpleGrantedAuthority(it) }
                val authentication = UsernamePasswordAuthenticationToken(userId, null, authorities)
                SecurityContextHolder.getContext().authentication = authentication
            }
        } catch (ex: Exception) {
            // Invalid token — leave context empty; security will reject if endpoint requires auth
        }

        filterChain.doFilter(request, response)
    }
}
