package com.kenlikdev.qmarket.network

import platform.Foundation.NSUserDefaults

/**
 * iOS: NSUserDefaults (app sandbox). Prefer Keychain for higher threat models later.
 */
class IosUserDefaultsSessionStore : SessionStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun readAccessToken(): String? = defaults.stringForKey(KEY_ACCESS)?.ifBlank { null }

    override fun readRefreshToken(): String? = defaults.stringForKey(KEY_REFRESH)?.ifBlank { null }

    override fun readEmail(): String? = defaults.stringForKey(KEY_EMAIL)?.ifBlank { null }

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) {
        defaults.setObject(accessToken, KEY_ACCESS)
        defaults.setObject(refreshToken, KEY_REFRESH)
        if (email != null) {
            defaults.setObject(email, KEY_EMAIL)
        } else {
            defaults.removeObjectForKey(KEY_EMAIL)
        }
        defaults.synchronize()
    }

    override fun clear() {
        defaults.removeObjectForKey(KEY_ACCESS)
        defaults.removeObjectForKey(KEY_REFRESH)
        defaults.removeObjectForKey(KEY_EMAIL)
        defaults.synchronize()
    }

    private companion object {
        const val KEY_ACCESS = "qmarket.accessToken"
        const val KEY_REFRESH = "qmarket.refreshToken"
        const val KEY_EMAIL = "qmarket.email"
    }
}

actual fun createPlatformSessionStore(): SessionStore = IosUserDefaultsSessionStore()
