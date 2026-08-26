package com.kenlikdev.qmarket.network

/**
 * Browser localStorage interop (origin-scoped).
 */
private external interface DomStorage {
    fun getItem(key: String): String?

    fun setItem(
        key: String,
        value: String,
    )

    fun removeItem(key: String)
}

@JsName("localStorage")
private external val browserLocalStorage: DomStorage

/**
 * Browser: localStorage (origin-scoped; not HTTP-only cookie security).
 */
class JsLocalStorageSessionStore : SessionStore {
    override fun readAccessToken(): String? = browserLocalStorage.getItem(KEY_ACCESS)?.ifBlank { null }

    override fun readRefreshToken(): String? = browserLocalStorage.getItem(KEY_REFRESH)?.ifBlank { null }

    override fun readEmail(): String? = browserLocalStorage.getItem(KEY_EMAIL)?.ifBlank { null }

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) {
        browserLocalStorage.setItem(KEY_ACCESS, accessToken)
        browserLocalStorage.setItem(KEY_REFRESH, refreshToken)
        if (email != null) {
            browserLocalStorage.setItem(KEY_EMAIL, email)
        } else {
            browserLocalStorage.removeItem(KEY_EMAIL)
        }
    }

    override fun clear() {
        browserLocalStorage.removeItem(KEY_ACCESS)
        browserLocalStorage.removeItem(KEY_REFRESH)
        browserLocalStorage.removeItem(KEY_EMAIL)
    }

    private companion object {
        const val KEY_ACCESS = "qmarket.accessToken"
        const val KEY_REFRESH = "qmarket.refreshToken"
        const val KEY_EMAIL = "qmarket.email"
    }
}

actual fun createPlatformSessionStore(): SessionStore = JsLocalStorageSessionStore()
