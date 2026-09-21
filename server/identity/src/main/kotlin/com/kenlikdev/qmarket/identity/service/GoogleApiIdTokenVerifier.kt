package com.kenlikdev.qmarket.identity.service

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier as GoogleApiVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.config.GoogleOAuthProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Production Google ID-token verifier backed by Google's official Java client.
 *
 * GoogleApiVerifier validates the token signature and standard issuer,
 * audience, and expiration claims before identity claims are exposed to QMarket.
 */
@Component
@ConditionalOnProperty(name = ["qmarket.security.oauth.google.enabled"], havingValue = "true")
class GoogleApiIdTokenVerifier(
    private val props: GoogleOAuthProperties,
) : GoogleIdTokenVerifier {
    private val verifier: GoogleApiVerifier by lazy {
        try {
            GoogleApiVerifier
                .Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                )
                .setAudience(props.normalizedClientIds())
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

        val verified =
            try {
                verifier.verify(token)
            } catch (_: IOException) {
                throw UnauthorizedException("Unable to verify Google ID token")
            } catch (_: GeneralSecurityException) {
                throw UnauthorizedException("Unable to verify Google ID token")
            }
                ?: throw UnauthorizedException("Invalid Google ID token")

        val payload = verified.payload
        val email =
            payload.email?.trim()?.lowercase()
                ?: throw UnauthorizedException("Google token missing email")
        val subject =
            payload.subject?.trim()
                ?: throw UnauthorizedException("Google token missing sub")
        val emailVerified = payload.emailVerified ?: false

        if (props.requireEmailVerified && !emailVerified) {
            throw UnauthorizedException("Google email is not verified")
        }

        return GoogleIdTokenClaims(
            subject = subject,
            email = email,
            emailVerified = emailVerified,
            givenName = payload.givenName?.trim()?.ifBlank { null },
            familyName = payload.familyName?.trim()?.ifBlank { null },
        )
    }
}
