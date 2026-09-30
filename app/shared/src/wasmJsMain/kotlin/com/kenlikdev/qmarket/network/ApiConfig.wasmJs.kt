package com.kenlikdev.qmarket.network

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsFun

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.QMARKET_API_BASE_URL || window.location.origin")
private external fun browserApiBaseUrl(): String

actual fun defaultApiBaseUrl(): String = browserApiBaseUrl()
