package com.kenlikdev.qmarket.network

/**
 * Default API base URL per platform is provided by [defaultApiBaseUrl].
 * Override for device / production builds.
 */
expect fun defaultApiBaseUrl(): String

/**
 * Validates an API base URL shared by all KMP targets.
 *
 * Plain HTTP is allowed only for explicitly local development hosts.
 * Remote endpoints must use HTTPS. Credentials, query strings and fragments
 * are rejected because this value is an API origin/base URL, not a request URI.
 */
fun validateApiBaseUrl(raw: String): String {
    val value = raw.trim().trimEnd('/')
    require(value.isNotEmpty()) { "API base URL must not be blank" }

    val match =
        Regex("""^(https?)://([^/?#]+)(/[^?#]*)?$""")
            .matchEntire(value)
            ?: throw IllegalArgumentException("API base URL must be an absolute HTTP(S) URL")

    val scheme = match.groupValues[1].lowercase()
    val authority = match.groupValues[2]
    require('@' !in authority) { "API base URL must not contain user credentials" }

    val host =
        when {
            authority.startsWith("[") -> authority.substringBefore(']').removePrefix("[")
            else -> authority.substringBefore(':')
        }.lowercase()

    require(host.isNotEmpty()) { "API base URL host must not be blank" }

    val localHost =
        host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "10.0.2.2"

    require(scheme == "https" || localHost) {
        "Remote API base URLs must use HTTPS"
    }

    return value
}
