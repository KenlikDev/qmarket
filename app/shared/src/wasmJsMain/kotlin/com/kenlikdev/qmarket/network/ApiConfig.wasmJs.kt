package com.kenlikdev.qmarket.network

actual fun defaultApiBaseUrl(): String =
    js("window.__QMARKET_API_BASE_URL || window.location.origin")
