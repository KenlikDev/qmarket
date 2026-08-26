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
        roles.any { r ->
            r == "ROLE_ADMIN" || r == "ADMIN" || r.endsWith("_ADMIN")
        }

    fun hasSession(): Boolean = !token.isNullOrBlank() || !refresh.isNullOrBlank()

    fun applyAuth(auth: AuthResponseDto) {
        token = auth.accessToken
        refresh = auth.refreshToken
        email = auth.user.email
        roles = auth.user.roles
        store.write(auth.accessToken, auth.refreshToken, auth.user.email)
    }

    fun applyRoles(newRoles: List<String>) {
        roles = newRoles
    }

    fun clear() {
        token = null
        refresh = null
        email = null
        roles = emptyList()
        store.clear()
    }
}
