package com.kenlikdev.qmarket.network

/**
 * Supplies a platform-specific API endpoint.
 *
 * Development defaults intentionally point to local endpoints. Production builds must
 * override the endpoint with HTTPS unless the endpoint is one of the local development hosts.
 */
expect fun defaultApiBaseUrl(): String

/**
 * Normalizes and validates the API endpoint before it is handed to Ktor.
 *
 * HTTPS is mandatory for non-local endpoints. Plain HTTP is allowed only for local development
 * hosts that never leave the device/host network boundary.
 */
internal fun validateApiBaseUrl(rawBaseUrl: String): String {
    val baseUrl = rawBaseUrl.trim().trimEnd('/')
    require(baseUrl.isNotBlank()) {
        "QMarket API base URL must not be blank"
    }

    val isHttps = baseUrl.startsWith("https://", ignoreCase = true)
    val isLocalHttp =
        Regex(
            "^http://(?:localhost|127(?:\\\\.\\d{1,3}){3}|10\\\\.0\\\\.2\\\\.2)(?::\\\\d{1,5})?(?:/.*)?$",
            RegexOption.IGNORE_CASE,
        ).matches(baseUrl)

    require(isHttps || isLocalHttp) {
        "QMarket API base URL must use HTTPS; HTTP is allowed only for localhost, 127.0.0.0/8, or Android emulator host 10.0.2.2"
    }
    return baseUrl
}
