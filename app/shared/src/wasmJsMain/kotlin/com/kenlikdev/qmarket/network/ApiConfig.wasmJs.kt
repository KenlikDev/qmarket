package com.kenlikdev.qmarket.network

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsFun

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun("() => window.location.origin")
private external fun browserOrigin(): String

actual fun defaultApiBaseUrl(): String = browserOrigin()
