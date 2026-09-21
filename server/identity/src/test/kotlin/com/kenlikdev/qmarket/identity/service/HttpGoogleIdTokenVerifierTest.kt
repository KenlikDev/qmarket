package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.config.GoogleOAuthProperties
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.net.http.HttpClient

class HttpGoogleIdTokenVerifierTest {
    private val props =
        GoogleOAuthProperties(
            enabled = true,
            clientIds = listOf("google-client-id"),
        )

    @Test
    fun `rejects oversized id token before network call`() {
        val httpClient = mockk<HttpClient>()
        val verifier =
            HttpGoogleIdTokenVerifier(
                props = props,
                objectMapper = jacksonObjectMapper(),
                httpClient = httpClient,
            )

        val exception =
            assertThrows<UnauthorizedException> {
                verifier.verify("x".repeat(16_385))
            }

        assertTrue(exception.message.orEmpty().contains("too long"))
    }
}
