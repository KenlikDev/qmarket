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
    private var record = StoredSessionRecord()

    override fun readAccessToken(): String? =
        record.takeUnless { it.cleared }?.accessToken?.ifBlank { null }

    override fun readRefreshToken(): String? =
        record.takeUnless { it.cleared }?.refreshToken?.ifBlank { null }

    override fun readEmail(): String? =
        record.takeUnless { it.cleared }?.email?.ifBlank { null }

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) {
        record =
            StoredSessionRecord(
                accessToken = accessToken,
                refreshToken = refreshToken,
                email = email,
            )
    }

    override fun clear() {
        record = StoredSessionRecord(cleared = true)
    }
}
/**
 * Creates the platform session store.
 * Android: call [initAndroidSessionStore] from MainActivity before [App].
 */
expect fun createPlatformSessionStore(): SessionStore
