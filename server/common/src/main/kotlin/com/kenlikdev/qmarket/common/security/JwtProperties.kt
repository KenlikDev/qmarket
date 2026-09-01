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
        require(s.length >= 32) {
            "qmarket.security.jwt.secret must be at least 32 characters (got ${s.length})."
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
