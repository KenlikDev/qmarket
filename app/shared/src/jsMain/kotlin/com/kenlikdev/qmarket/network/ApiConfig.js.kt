package com.kenlikdev.qmarket.network

@Suppress("UNUSED_VARIABLE")
private fun browserApiBaseUrl(): String =
    js("window.QMARKET_API_BASE_URL || window.location.origin") as String

actual fun defaultApiBaseUrl(): String = browserApiBaseUrl()
