package com.kenlikdev.qmarket.network

import kotlin.JsFun
import kotlin.js.ExperimentalWasmJsInterop

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.QMARKET_API_BASE_URL || window.location.origin")
private external fun browserApiBaseUrl(): String

actual fun defaultApiBaseUrl(): String = browserApiBaseUrl()
