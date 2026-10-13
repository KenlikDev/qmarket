package com.kenlikdev.qmarket.identity.service

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.config.GoogleOAuthProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import java.io.IOException
import java.security.GeneralSecurityException
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier as GoogleApiVerifier

/**
 * Production Google ID-token verifier backed by Google's official Java client.
 *
 * The verifier validates Google's signature and the standard issuer, audience,
 * and expiration claims before this adapter exposes the identity claims used by QMarket.
 */
@Component
@Primary
@ConditionalOnProperty(name = ["qmarket.security.oauth.google.enabled"], havingValue = "true")
class GoogleApiIdTokenVerifier(
    private val props: GoogleOAuthProperties,
) : GoogleIdTokenVerifier {
    private val audiences =
        props.clientIds
            .map(String::trim)
            .filter(String::isNotEmpty)

    init {
        require(audiences.isNotEmpty()) { "At least one Google OAuth client ID must be configured" }
    }

    private val verifier: GoogleApiVerifier by lazy {
        try {
            GoogleApiVerifier
                .Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                )
                .setAudience(audiences)
                .build()
        } catch (ex: GeneralSecurityException) {
            throw IllegalStateException("Unable to initialize Google ID token verification", ex)
        } catch (ex: IOException) {
            throw IllegalStateException("Unable to initialize Google ID token verification", ex)
        }
    }

    override fun verify(idToken: String): GoogleIdTokenClaims {
        val token = idToken.trim()
        if (token.isEmpty()) {
            throw UnauthorizedException("Google idToken must not be blank")
        }
        if (token.length > MAX_ID_TOKEN_LENGTH) {
            throw UnauthorizedException("Google idToken is too long")
        }

        val verified =
            try {
                verifier.verify(token)
            } catch (_: IOException) {
                throw UnauthorizedException("Unable to verify Google ID token")
            } catch (_: GeneralSecurityException) {
                throw UnauthorizedException("Unable to verify Google ID token")
            } ?: throw UnauthorizedException("Invalid Google ID token")

        val payload = verified.payload
        val email =
            payload.email
                ?.trim()
                ?.lowercase()
                ?.ifBlank { null }
                ?: throw UnauthorizedException("Google token missing email")
        val subject =
            payload.subject
                ?.trim()
                ?.ifBlank { null }
                ?: throw UnauthorizedException("Google token missing sub")
        val emailVerified = payload.emailVerified ?: false
        if (props.requireEmailVerified && !emailVerified) {
            throw UnauthorizedException("Google email is not verified")
        }

        return GoogleIdTokenClaims(
            subject = subject,
            email = email,
            emailVerified = emailVerified,
            givenName = (payload["given_name"] as? String)?.trim()?.ifBlank { null },
            familyName = (payload["family_name"] as? String)?.trim()?.ifBlank { null },
        )
    }

    private companion object {
        const val MAX_ID_TOKEN_LENGTH = 16_384
    }
}
