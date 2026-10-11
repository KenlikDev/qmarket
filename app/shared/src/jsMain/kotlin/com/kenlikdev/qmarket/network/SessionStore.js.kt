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

private object BrowserLocalStorageKeyValueStore : SessionKeyValueStore {
    override fun get(key: String): String? = browserLocalStorage.getItem(key)

    override fun put(
        key: String,
        value: String,
    ) {
        // localStorage.setItem is synchronous and replaces the one canonical value atomically.
        browserLocalStorage.setItem(key, value)
    }

    override fun remove(key: String) {
        browserLocalStorage.removeItem(key)
    }
}

/**
 * Browser session persistence uses one serialized record instead of three independently updated
 * localStorage entries. This improves consistency, but localStorage remains readable by same-origin
 * JavaScript and is not equivalent to an HttpOnly cookie.
 */
class JsLocalStorageSessionStore : SessionStore by AtomicSessionRecordStore(
    storage = BrowserLocalStorageKeyValueStore,
    legacyKeys =
        LegacySessionKeys(
            accessToken = "qmarket.accessToken",
            refreshToken = "qmarket.refreshToken",
            email = "qmarket.email",
        ),
)

actual fun createPlatformSessionStore(): SessionStore = JsLocalStorageSessionStore()
