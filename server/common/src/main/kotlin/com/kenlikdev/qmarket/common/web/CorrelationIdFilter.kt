package com.kenlikdev.qmarket.common.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Propagates / generates a correlation id for every HTTP request.
 *
 * Accepts [HEADER] or [HEADER_ALT] from the client; otherwise generates a UUID.
 * Puts the value in SLF4J [MDC] under [MDC_KEY] and echoes it on the response.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val incoming =
            sequenceOf(
                request.getHeader(HEADER),
                request.getHeader(HEADER_ALT),
            ).mapNotNull { it?.trim() }
                .firstOrNull(::isSafeCorrelationId)
        val correlationId = incoming ?: UUID.randomUUID().toString()
        MDC.put(MDC_KEY, correlationId)
        response.setHeader(HEADER, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(MDC_KEY)
        }
    }

    private fun isSafeCorrelationId(value: String): Boolean =
        value.length <= MAX_LENGTH &&
            value.isNotEmpty() &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

    companion object {
        private const val MAX_LENGTH = 128
        const val HEADER = "X-Correlation-Id"
        const val HEADER_ALT = "X-Request-Id"
        const val MDC_KEY = "correlationId"
    }
}
