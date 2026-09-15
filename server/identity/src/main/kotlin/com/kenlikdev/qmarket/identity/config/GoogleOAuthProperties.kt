package com.kenlikdev.qmarket.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "qmarket.security.oauth.google")
data class GoogleOAuthProperties(
    val enabled: Boolean = false,
    val clientIds: List<String> = emptyList(),
    val requireEmailVerified: Boolean = true,
)
