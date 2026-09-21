package com.kenlikdev.qmarket.common.web

import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()

    @Test
    fun `generates correlation id when missing`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        var mdcDuringChain: String? = null
        val chain =
            FilterChain { _, _ ->
                mdcDuringChain = MDC.get(CorrelationIdFilter.MDC_KEY)
            }

        filter.doFilter(request, response, chain)

        assertNotNull(mdcDuringChain)
        assertEquals(mdcDuringChain, response.getHeader(CorrelationIdFilter.HEADER))
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY))
    }

    @Test
    fun `reuses client X-Correlation-Id`() {
        val request = MockHttpServletRequest()
        request.addHeader(CorrelationIdFilter.HEADER, "client-corr-1")
        val response = MockHttpServletResponse()
        var mdcDuringChain: String? = null
        val chain =
            FilterChain { _, _ ->
                mdcDuringChain = MDC.get(CorrelationIdFilter.MDC_KEY)
            }

        filter.doFilter(request, response, chain)

        assertEquals("client-corr-1", mdcDuringChain)
        assertEquals("client-corr-1", response.getHeader(CorrelationIdFilter.HEADER))
    }

    @Test
    fun `accepts X-Request-Id as alternate`() {
        val request = MockHttpServletRequest()
        request.addHeader(CorrelationIdFilter.HEADER_ALT, "req-42")
        val response = MockHttpServletResponse()
        var mdcDuringChain: String? = null
        val chain =
            FilterChain { _, _ ->
                mdcDuringChain = MDC.get(CorrelationIdFilter.MDC_KEY)
            }

        filter.doFilter(request, response, chain)

        assertEquals("req-42", mdcDuringChain)
    }
    @Test
    fun `rejects unsafe correlation id and generates a new one`() {
        val request = MockHttpServletRequest()
        request.addHeader(CorrelationIdFilter.HEADER, "unsafe\\r\\nheader")
        val response = MockHttpServletResponse()
        var mdcDuringChain: String? = null
        val chain =
            FilterChain { _, _ ->
                mdcDuringChain = MDC.get(CorrelationIdFilter.MDC_KEY)
            }

        filter.doFilter(request, response, chain)

        assertNotNull(mdcDuringChain)
        assertNotNull(response.getHeader(CorrelationIdFilter.HEADER))
        assertEquals(mdcDuringChain, response.getHeader(CorrelationIdFilter.HEADER))
        assertNotEquals("unsafe\\r\\nheader", mdcDuringChain)
    }

    @Test
    fun `rejects correlation id longer than the limit`() {
        val request = MockHttpServletRequest()
        request.addHeader(CorrelationIdFilter.HEADER, "x".repeat(129))
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        val generated = response.getHeader(CorrelationIdFilter.HEADER)
        assertNotNull(generated)
        assertEquals(36, generated.length)
    }

}
