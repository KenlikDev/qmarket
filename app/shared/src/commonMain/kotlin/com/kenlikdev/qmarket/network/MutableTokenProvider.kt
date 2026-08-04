package com.kenlikdev.qmarket.network

import kotlin.concurrent.Volatile

/**
 * In-memory token holder for the UI session (replace with secure storage later).
 */
class MutableTokenProvider : TokenProvider {
    @Volatile
    var token: String? = null

    override fun accessToken(): String? = token

    fun clear() {
        token = null
    }
}
