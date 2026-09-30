package com.kenlikdev.qmarket.network

actual fun defaultApiBaseUrl(): String =
    System.getProperty("qmarket.api.base-url")?.takeIf { it.isNotBlank() }
        ?: System.getenv("QMARKET_API_BASE_URL")?.takeIf { it.isNotBlank() }
        ?: "http://localhost:8080"
