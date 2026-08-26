package com.kenlikdev.qmarket.network

/**
 * wasmJs: in-memory until a stable localStorage binding is added for this target.
 * Session is lost on full page reload (same as previous MutableTokenProvider behaviour).
 */
actual fun createPlatformSessionStore(): SessionStore = InMemorySessionStore()
