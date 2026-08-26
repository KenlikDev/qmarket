package com.kenlikdev.qmarket.network

/**
 * Platform-backed persistence for JWT session (access + refresh + email for UI).
 * Implementations use app-private storage; not a substitute for server-side revocation.
 */
interface SessionStore {
    fun readAccessToken(): String?

    fun readRefreshToken(): String?

    fun readEmail(): String?

    fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    )

    fun clear()
}

/** In-memory store for tests and platforms that have not initialized persistence. */
class InMemorySessionStore : SessionStore {
    private var access: String? = null
    private var refresh: String? = null
    private var email: String? = null

    override fun readAccessToken(): String? = access

    override fun readRefreshToken(): String? = refresh

    override fun readEmail(): String? = email

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) {
        this.access = accessToken
        this.refresh = refreshToken
        this.email = email
    }

    override fun clear() {
        access = null
        refresh = null
        email = null
    }
}

/**
 * Creates the platform session store.
 * Android: call [initAndroidSessionStore] from MainActivity before [App].
 */
expect fun createPlatformSessionStore(): SessionStore
