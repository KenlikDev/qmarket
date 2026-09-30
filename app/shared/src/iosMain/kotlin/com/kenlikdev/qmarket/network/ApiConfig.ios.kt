package com.kenlikdev.qmarket.network

import platform.Foundation.NSBundle

actual fun defaultApiBaseUrl(): String {
    val baseUrl =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("QMARKET_API_BASE_URL") as? String)
            ?.takeIf { it.isNotBlank() }
            ?: error("QMARKET_API_BASE_URL is not configured")
    val configuration =
        (NSBundle.mainBundle.objectForInfoDictionaryKey("QMARKET_BUILD_CONFIGURATION") as? String)
            ?.trim()
            ?.uppercase()
            ?: error("QMARKET_BUILD_CONFIGURATION is not configured")

    val validated = validateApiBaseUrl(baseUrl)
    require(configuration != "RELEASE" || validated.startsWith("https://")) {
        "iOS release API base URL must use HTTPS"
    }
    return validated
}
