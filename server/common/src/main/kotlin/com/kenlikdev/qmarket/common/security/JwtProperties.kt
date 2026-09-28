package com.kenlikdev.qmarket.common.security

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "qmarket.security.jwt")
data class JwtProperties(
    val secret: String = "",
    val accessTokenExpirationMs: Long = 900_000,
    val refreshTokenExpirationMs: Long = 604_800_000,
) {
    @PostConstruct
    fun validate() {
        val s = secret.trim()
        require(s.isNotEmpty()) {
            "qmarket.security.jwt.secret is required. Set JWT_SECRET or use profile 'dev' / 'test'."
        }
        val byteLength = s.toByteArray(Charsets.UTF_8).size
        require(byteLength >= 32) {
            "qmarket.security.jwt.secret must contain at least 32 UTF-8 bytes (got $byteLength)."
        }
        require(accessTokenExpirationMs > 0) {
            "qmarket.security.jwt.access-token-expiration-ms must be greater than 0."
        }
        require(refreshTokenExpirationMs > 0) {
            "qmarket.security.jwt.refresh-token-expiration-ms must be greater than 0."
        }
        val weak =
            setOf(
                "change-me-to-a-very-long-and-secure-secret-key-at-least-256-bits",
                "change-me",
                "change-me-in-production-to-a-long-secret-key-256bits",
                "dev-only-qmarket-jwt-secret-key-32chars-min",
            )
        require(s !in weak) {
            "qmarket.security.jwt.secret must not use the known placeholder value. Set JWT_SECRET."
        }
    }
}
