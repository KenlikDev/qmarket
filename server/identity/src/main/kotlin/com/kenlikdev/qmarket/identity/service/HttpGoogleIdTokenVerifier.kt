package com.kenlikdev.qmarket.identity.service

import com.kenlikdev.qmarket.common.exception.UnauthorizedException
import com.kenlikdev.qmarket.identity.config.GoogleOAuthProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
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
        val email =
            extract(body, "email")?.lowercase()
                ?: throw UnauthorizedException("Google token missing email")
        val aud =
            extract(body, "aud")
                ?: throw UnauthorizedException("Google token missing aud")
        val allowed =
            props.clientIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toSet()
        if (allowed.isNotEmpty() && aud !in allowed) {
            throw UnauthorizedException("Google token audience is not allowed")
        }
        val emailVerified = extract(body, "email_verified")?.equals("true", ignoreCase = true) == true
        if (props.requireEmailVerified && !emailVerified) {
            throw UnauthorizedException("Google email is not verified")
        }
        val sub =
            extract(body, "sub")
                ?: throw UnauthorizedException("Google token missing sub")
        return GoogleIdTokenClaims(
            subject = sub,
            email = email,
            emailVerified = emailVerified,
            givenName = extract(body, "given_name"),
            familyName = extract(body, "family_name"),
        )
    }

    private fun extract(
        json: String,
        field: String,
    ): String? {
        val key = "\"" + field + "\""
        val keyIdx = json.indexOf(key)
        if (keyIdx < 0) return null
        val colon = json.indexOf(':', keyIdx + key.length)
        if (colon < 0) return null
        val firstQuote = json.indexOf('"', colon + 1)
        if (firstQuote < 0) return null
        val secondQuote = json.indexOf('"', firstQuote + 1)
        if (secondQuote < 0) return null
        return json.substring(firstQuote + 1, secondQuote)
    }
}
