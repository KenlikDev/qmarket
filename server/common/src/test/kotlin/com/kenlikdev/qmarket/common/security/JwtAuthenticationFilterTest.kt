package com.kenlikdev.qmarket.common.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.JwtException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import java.util.UUID

class JwtAuthenticationFilterTest {
    private lateinit var jwtService: JwtService
    private lateinit var filter: JwtAuthenticationFilter

    @BeforeEach
    fun setUp() {
        jwtService = mockk()
        filter = JwtAuthenticationFilter(jwtService)
        SecurityContextHolder.clearContext()
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `missing Authorization header continues chain without authentication`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(request, chain.request)
        verify(exactly = 0) { jwtService.parseClaims(any()) }
    }

    @Test
    fun `non-Bearer scheme is ignored`() {
        val request = MockHttpServletRequest().apply { addHeader("Authorization", "Basic abc") }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        verify(exactly = 0) { jwtService.parseClaims(any()) }
    }

    @Test
    fun `valid access token sets SecurityContext with roles`() {
        val userId = UUID.randomUUID()
        val claims = mockk<Claims>(relaxed = true)
        every { jwtService.parseClaims("good-token") } returns claims
        every { jwtService.isAccessToken(claims) } returns true
        every { jwtService.getUserId(claims) } returns userId
        every { claims["roles"] } returns listOf("ROLE_USER", "ROLE_ADMIN")
        every { claims.get("roles", any<Class<*>>()) } returns listOf("ROLE_USER", "ROLE_ADMIN")

        val request =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer good-token")
            }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        val auth =
            requireNotNull(SecurityContextHolder.getContext().authentication) {
                "SecurityContext.authentication was null after valid access token"
            }
        assertEquals(userId, auth.principal)
        assertTrue(auth.authorities.contains(SimpleGrantedAuthority("ROLE_USER")))
        assertTrue(auth.authorities.contains(SimpleGrantedAuthority("ROLE_ADMIN")))
        assertEquals(request, chain.request)
    }

    @Test
    fun `refresh token does not set authentication`() {
        val claims = mockk<Claims>(relaxed = true)
        every { jwtService.parseClaims("refresh-token") } returns claims
        every { jwtService.isAccessToken(claims) } returns false

        val request =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer refresh-token")
            }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(request, chain.request)
    }

    @Test
    fun `invalid token is swallowed and chain continues`() {
        every { jwtService.parseClaims("bad") } throws JwtException("jwt expired")

        val request =
            MockHttpServletRequest().apply {
                addHeader("Authorization", "Bearer bad")
            }
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()

        filter.doFilter(request, response, chain)

        assertNull(SecurityContextHolder.getContext().authentication)
        assertEquals(request, chain.request)
    }
}
