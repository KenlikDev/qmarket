package com.kenlikdev.qmarket.network

import android.content.Context
import android.content.SharedPreferences

/**
 * Android: app-private SharedPreferences (MODE_PRIVATE).
 * Call [initAndroidSessionStore] from MainActivity before composing [com.kenlikdev.qmarket.App].
 * If not initialized (Compose Preview), falls back to [InMemorySessionStore].
 */
private var androidAppContext: Context? = null

fun initAndroidSessionStore(context: Context) {
    androidAppContext = context.applicationContext
}

class AndroidPrefsSessionStore(
    context: Context,
) : SessionStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun readAccessToken(): String? = prefs.getString(KEY_ACCESS, null)?.ifBlank { null }

    override fun readRefreshToken(): String? = prefs.getString(KEY_REFRESH, null)?.ifBlank { null }

    override fun readEmail(): String? = prefs.getString(KEY_EMAIL, null)?.ifBlank { null }

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) {
        prefs.edit()
            .putString(KEY_ACCESS, accessToken)
            .putString(KEY_REFRESH, refreshToken)
            .putString(KEY_EMAIL, email)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_NAME = "qmarket_session"
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_EMAIL = "email"
    }
}

actual fun createPlatformSessionStore(): SessionStore {
    val ctx = androidAppContext
    return if (ctx != null) {
        AndroidPrefsSessionStore(ctx)
    } else {
        InMemorySessionStore()
    }
}
