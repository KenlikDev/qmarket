package com.kenlikdev.qmarket.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

private var androidAppContext: Context? = null

fun initAndroidSessionStore(context: Context) {
    androidAppContext = context.applicationContext
}

/**
 * Android session store backed by EncryptedSharedPreferences (AES-256).
 * The session itself is one encrypted value, committed synchronously so storage failures surface.
 */
class AndroidEncryptedSessionStore(
    context: Context,
) : SessionStore by AtomicSessionRecordStore(
        storage = EncryptedPrefsKeyValueStore(context),
        legacyKeys =
            LegacySessionKeys(
                accessToken = LEGACY_KEY_ACCESS,
                refreshToken = LEGACY_KEY_REFRESH,
                email = LEGACY_KEY_EMAIL,
            ),
    )

/** Key-value adapter used by [AtomicSessionRecordStore]; each put/remove is a committed editor. */
private class EncryptedPrefsKeyValueStore(
    context: Context,
) : SessionKeyValueStore {
    private val prefs: SharedPreferences = createEncryptedPrefs(context)

    init {
        migrateFromPlainPrefsIfNeeded(context, prefs)
    }

    override fun get(key: String): String? = prefs.getString(key, null)?.ifBlank { null }

    override fun put(
        key: String,
        value: String,
    ) {
        check(prefs.edit().putString(key, value).commit()) {
            "Unable to persist Android session storage"
        }
    }

    override fun remove(key: String) {
        check(prefs.edit().remove(key).commit()) {
            "Unable to remove Android session storage entry: $key"
        }
    }

    private companion object {
        const val ENCRYPTED_PREFS_NAME = "qmarket_session_secure"
        const val LEGACY_PREFS_NAME = "qmarket_session"
        const val SESSION_RECORD_KEY = "qmarket.session.v2"
        const val LEGACY_KEY_ACCESS = "accessToken"
        const val LEGACY_KEY_REFRESH = "refreshToken"
        const val LEGACY_KEY_EMAIL = "email"

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

        /**
         * One-time migration from the old plain preferences file to the encrypted preferences.
         * Never re-import plaintext tokens if the canonical record or encrypted legacy keys exist:
         * a cleared canonical tombstone must take precedence over stale legacy data.
         */
        fun migrateFromPlainPrefsIfNeeded(
            context: Context,
            encrypted: SharedPreferences,
        ) {
            val legacy =
                context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)

            if (encrypted.contains(SESSION_RECORD_KEY) ||
                encrypted.contains(LEGACY_KEY_ACCESS) ||
                encrypted.contains(LEGACY_KEY_REFRESH)
            ) {
                if (legacy.all.isNotEmpty()) {
                    check(legacy.edit().clear().commit()) {
                        "Unable to clear stale plaintext Android session storage"
                    }
                }
                return
            }

            val access = legacy.getString(LEGACY_KEY_ACCESS, null)
            val refresh = legacy.getString(LEGACY_KEY_REFRESH, null)
            val email = legacy.getString(LEGACY_KEY_EMAIL, null)
            if (access.isNullOrBlank() && refresh.isNullOrBlank()) {
                if (legacy.all.isNotEmpty()) {
                    check(legacy.edit().clear().commit()) {
                        "Unable to clear stale plaintext Android session storage"
                    }
                }
                return
            }

            check(
                encrypted.edit()
                    .putString(LEGACY_KEY_ACCESS, access)
                    .putString(LEGACY_KEY_REFRESH, refresh)
                    .putString(LEGACY_KEY_EMAIL, email)
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

private const val LEGACY_KEY_ACCESS = "accessToken"
private const val LEGACY_KEY_REFRESH = "refreshToken"
private const val LEGACY_KEY_EMAIL = "email"

actual fun createPlatformSessionStore(): SessionStore {
    val context = androidAppContext
    return if (context != null) {
        AndroidEncryptedSessionStore(context)
    } else {
        InMemorySessionStore()
    }
}
