package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AuthResponseDto
import kotlin.concurrent.Volatile

/**
 * In-memory session tokens (replace with secure storage later).
 */
class MutableTokenProvider : TokenProvider {
    @Volatile
    var token: String? = null

    @Volatile
    var refresh: String? = null

    override fun accessToken(): String? = token

    override fun refreshToken(): String? = refresh

    fun applyAuth(auth: AuthResponseDto) {
        token = auth.accessToken
        refresh = auth.refreshToken
    }

    fun clear() {
        token = null
        refresh = null
    }
}
