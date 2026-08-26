package com.kenlikdev.qmarket.common.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "qmarket.security.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpirationMs: Long = 900_000,
    val refreshTokenExpirationMs: Long = 604_800_000,
)
