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

    /** Full administrator permissions, including user management and destructive actions. */
    fun isAdmin(): Boolean = hasAnyRole("ADMIN")

    /** Catalog/order staff permissions shared by administrators and store managers. */
    fun canManageStore(): Boolean = hasAnyRole("ADMIN", "MANAGER")

    private fun hasAnyRole(vararg expectedRoles: String): Boolean {
        val expected = expectedRoles.map(::normalizeRole).toSet()
        return roles.any { role -> normalizeRole(role) in expected }
    }

    private fun normalizeRole(role: String): String =
        role.trim().uppercase().removePrefix("ROLE_")

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
        } catch (exception: SessionClearedWithCleanupFailure) {
            // The durable tombstone committed successfully; only obsolete-key cleanup failed.
            // The caller receives the warning, but memory must match the persisted logged-out state.
            clearMemory()
            throw exception
        }

        // A generic storage failure means persistence did not commit the clear. Retain memory so
        // callers can report failure without leaving the application in a split logged-out state.
        clearMemory()
    }

    private fun clearMemory() {
        token = null
        refresh = null
        email = null
        roles = emptyList()
    }
}
