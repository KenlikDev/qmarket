package com.kenlikdev.qmarket.network

import platform.Foundation.NSBundle

actual fun defaultApiBaseUrl(): String =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("QMARKET_API_BASE_URL") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "http://localhost:8080"
