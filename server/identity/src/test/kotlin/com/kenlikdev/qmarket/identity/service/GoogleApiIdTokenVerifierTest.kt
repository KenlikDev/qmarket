package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.config.GoogleOAuthProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class GoogleApiIdTokenVerifierTest {
    private val props =
        GoogleOAuthProperties(
            enabled = true,
            clientIds = listOf("google-client-id"),
        )

    private val verifier = GoogleApiIdTokenVerifier(props)

    @Test
    fun `rejects blank id token`() {
        val exception =
            assertThrows<UnauthorizedException> {
                verifier.verify("   ")
            }

        assertEquals("Google idToken must not be blank", exception.message)
    }

    @Test
    fun `rejects oversized id token before verifier initialization`() {
        val exception =
            assertThrows<UnauthorizedException> {
                verifier.verify("x".repeat(16_385))
            }

        assertEquals("Google idToken is too long", exception.message)
    }
}
