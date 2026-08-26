package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AuthResponseDto
import kotlin.concurrent.Volatile

/**
 * Session tokens with optional [SessionStore] persistence across process restarts.
 */
class MutableTokenProvider(
    private val store: SessionStore = InMemorySessionStore(),
) : TokenProvider {
    @Volatile
    private var token: String? = store.readAccessToken()

    @Volatile
    private var refresh: String? = store.readRefreshToken()

    @Volatile
    private var email: String? = store.readEmail()

    override fun accessToken(): String? = token

    override fun refreshToken(): String? = refresh

    fun sessionEmail(): String? = email

    fun hasSession(): Boolean = !token.isNullOrBlank() || !refresh.isNullOrBlank()

    fun applyAuth(auth: AuthResponseDto) {
        token = auth.accessToken
        refresh = auth.refreshToken
        email = auth.user.email
        store.write(auth.accessToken, auth.refreshToken, auth.user.email)
    }

    fun clear() {
        token = null
        refresh = null
        email = null
        store.clear()
    }
}
