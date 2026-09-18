package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.config.GoogleOAuthProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

@Component
@ConditionalOnProperty(name = ["qmarket.security.oauth.google.enabled"], havingValue = "true")
class HttpGoogleIdTokenVerifier(
    private val props: GoogleOAuthProperties,
    private val objectMapper: ObjectMapper,
    private val httpClient: HttpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
) : GoogleIdTokenVerifier {
    override fun verify(idToken: String): GoogleIdTokenClaims {
        val token = idToken.trim()
        if (token.isEmpty()) {
            throw UnauthorizedException("Google idToken must not be blank")
        }

        val uri =
            URI.create(
                "https://oauth2.googleapis.com/tokeninfo?id_token=" +
                    URLEncoder.encode(token, StandardCharsets.UTF_8),
            )
        val request =
            HttpRequest
                .newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw UnauthorizedException("Invalid Google ID token")
        }

        val body = response.body()
        val root =
            try {
                objectMapper.readTree(body)
            } catch (_: Exception) {
                throw UnauthorizedException("Invalid Google token response")
            }

        val email =
            root.path("email").asString(null)?.trim()?.lowercase()
                ?: throw UnauthorizedException("Google token missing email")
        val audience =
            root.path("aud").asString(null)?.trim()
                ?: throw UnauthorizedException("Google token missing aud")

        if (audience !in props.normalizedClientIds()) {
            throw UnauthorizedException("Google token audience is not allowed")
        }

        val emailVerified = root.path("email_verified").asBoolean(false)
        if (props.requireEmailVerified && !emailVerified) {
            throw UnauthorizedException("Google email is not verified")
        }

        val subject =
            root.path("sub").asString(null)?.trim()
                ?: throw UnauthorizedException("Google token missing sub")

        return GoogleIdTokenClaims(
            subject = subject,
            email = email,
            emailVerified = emailVerified,
            givenName = root.path("given_name").asString(null),
            familyName = root.path("family_name").asString(null),
        )
    }
}
