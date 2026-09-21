package com.kenlikdev.qmarket.identity.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "qmarket.security.oauth.google")
data class GoogleOAuthProperties(
    val enabled: Boolean = false,
    val clientIds: List<String> = emptyList(),
    val requireEmailVerified: Boolean = true,
) {
    init {
        val normalizedClientIds = clientIds.map(String::trim).filter(String::isNotEmpty).toSet()
        require(!enabled || normalizedClientIds.isNotEmpty()) {
            "At least one Google OAuth client ID is required when Google OAuth is enabled"
        }
    }

    fun normalizedClientIds(): Set<String> =
        clientIds
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
}
