package com.kenlikdev.qmarket.network

/**
 * Supplies JWT tokens for authenticated requests.
 * UI layer (or a secure store) implements this.
 */
interface TokenProvider {
    fun accessToken(): String?

    fun refreshToken(): String? = null
}
