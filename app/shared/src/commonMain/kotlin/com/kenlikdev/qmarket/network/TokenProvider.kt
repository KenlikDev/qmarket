package com.kenlikdev.qmarket.network

/**
 * Supplies the current JWT access token for authenticated requests.
 * UI layer (or a secure store) implements this.
 */
fun interface TokenProvider {
    fun accessToken(): String?
}
