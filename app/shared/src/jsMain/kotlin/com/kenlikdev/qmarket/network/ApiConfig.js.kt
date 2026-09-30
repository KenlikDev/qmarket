package com.kenlikdev.qmarket.network

actual fun defaultApiBaseUrl(): String =
    js("window.location.origin") as String
