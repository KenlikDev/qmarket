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

    @Volatile
    private var roles: List<String> = emptyList()

    override fun accessToken(): String? = token

    override fun refreshToken(): String? = refresh

    fun sessionEmail(): String? = email

    fun sessionRoles(): List<String> = roles

    fun isAdmin(): Boolean =
        roles.any { role ->
            role == "ROLE_ADMIN" || role == "ADMIN"
        }

    fun hasSession(): Boolean = !token.isNullOrBlank() || !refresh.isNullOrBlank()

    fun applyAuth(auth: AuthResponseDto) {
        store.write(auth.accessToken, auth.refreshToken, auth.user.email)
        token = auth.accessToken
        refresh = auth.refreshToken
        email = auth.user.email
        roles = auth.user.roles
    }

    fun applyRoles(newRoles: List<String>) {
        roles = newRoles
    }

    fun clear() {
        try {
            store.clear()
        } finally {
            token = null
            refresh = null
            email = null
            roles = emptyList()
        }
    }
}
