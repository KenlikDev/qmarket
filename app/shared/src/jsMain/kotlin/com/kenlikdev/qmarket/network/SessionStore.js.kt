package com.kenlikdev.qmarket.network

/**
 * Browser session tokens never persist in browser storage. The refresh token is held by an HttpOnly
 * cookie and the short-lived access token stays in memory. Previous token-bearing localStorage
 * entries are removed at startup so upgrades do not leave old persistent credentials behind.
 */
private external interface DomStorage {
    fun removeItem(key: String)
}

@JsName("localStorage")
private external val browserLocalStorage: DomStorage

private fun removeLegacyBrowserSessionTokens() {
    val hasWindow = js("typeof window !== 'undefined'") as Boolean
    if (!hasWindow) return

    try {
        val hasLocalStorage = js("typeof window.localStorage !== 'undefined'") as Boolean
        if (!hasLocalStorage) return
        listOf(
            "qmarket.session.v2",
            "qmarket.accessToken",
            "qmarket.refreshToken",
            "qmarket.email",
        ).forEach(browserLocalStorage::removeItem)
    } catch (exception: Throwable) {
        throw IllegalStateException("Unable to remove legacy persistent browser session tokens", exception)
    }
}

private class JsBrowserCookieSessionStore : BrowserCookieSessionStore {
    private val delegate = InMemorySessionStore()

    override fun readAccessToken(): String? = delegate.readAccessToken()

    override fun readRefreshToken(): String? = delegate.readRefreshToken()

    override fun readEmail(): String? = delegate.readEmail()

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) = delegate.write(accessToken, refreshToken, email)

    override fun clear() = delegate.clear()
}

actual fun createPlatformSessionStore(): SessionStore {
    removeLegacyBrowserSessionTokens()
    return JsBrowserCookieSessionStore()
}
