package com.kenlikdev.qmarket.network

actual fun defaultApiBaseUrl(): String =
    System.getProperty("qmarket.api.base-url")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: System.getenv("QMARKET_API_BASE_URL")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        ?: "http://localhost:8080"
