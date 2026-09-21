package com.kenlikdev.qmarket.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Android session store backed by [EncryptedSharedPreferences] (AES-256).
 * Call [initAndroidSessionStore] from MainActivity before composing [com.kenlikdev.qmarket.App].
 * If not initialized (Compose Preview), falls back to [InMemorySessionStore].
 *
 * On first use, migrates tokens from the legacy plain [SharedPreferences] file
 * `qmarket_session` (if present) into the encrypted store and clears the plain file.
 *
 * Uses androidx.security:security-crypto 1.0.0 API ([MasterKeys], not MasterKey).
 */
private var androidAppContext: Context? = null

fun initAndroidSessionStore(context: Context) {
    androidAppContext = context.applicationContext
}

class AndroidEncryptedSessionStore(
    context: Context,
) : SessionStore {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    init {
        migrateFromPlainPrefsIfNeeded(context, prefs)
    }

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
        check(prefs.edit().clear().commit()) {
            "Unable to clear Android session storage"
        }
    }

    private companion object {
        const val ENCRYPTED_PREFS_NAME = "qmarket_session_secure"
        const val LEGACY_PREFS_NAME = "qmarket_session"
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_EMAIL = "email"

        fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                ENCRYPTED_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }

        fun migrateFromPlainPrefsIfNeeded(
            context: Context,
            encrypted: SharedPreferences,
        ) {
            if (encrypted.contains(KEY_ACCESS) || encrypted.contains(KEY_REFRESH)) return
            val legacy =
                context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            val access = legacy.getString(KEY_ACCESS, null)
            val refresh = legacy.getString(KEY_REFRESH, null)
            if (access.isNullOrBlank() && refresh.isNullOrBlank()) return
            check(
                encrypted.edit()
                    .putString(KEY_ACCESS, access)
                    .putString(KEY_REFRESH, refresh)
                    .putString(KEY_EMAIL, legacy.getString(KEY_EMAIL, null))
                    .commit(),
            ) {
                "Unable to migrate Android session storage"
            }
            check(legacy.edit().clear().commit()) {
                "Unable to clear legacy Android session storage"
            }
        }
    }
}

actual fun createPlatformSessionStore(): SessionStore {
    val ctx = androidAppContext
    return if (ctx != null) {
        AndroidEncryptedSessionStore(ctx)
    } else {
        InMemorySessionStore()
    }
}
