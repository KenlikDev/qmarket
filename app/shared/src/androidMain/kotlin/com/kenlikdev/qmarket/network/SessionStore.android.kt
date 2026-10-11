package com.kenlikdev.qmarket.network

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private var androidAppContext: Context? = null

fun initAndroidSessionStore(context: Context) {
    androidAppContext = context.applicationContext
}

/**
 * Android session store using an AES-GCM key held by Android Keystore.
 *
 * The session tuple remains one encrypted SharedPreferences value, committed synchronously.
 * The deprecated EncryptedSharedPreferences API is used only as a one-time read-only migration
 * source for sessions created by older versions; all current writes use Android Keystore directly.
 */
class AndroidKeystoreSessionStore(
    context: Context,
) : SessionStore by createAndroidSessionStore(context)

private fun createAndroidSessionStore(context: Context): SessionStore {
    val target = AndroidKeystoreSessionKeyValueStore(context)
    val previousEncryptedPreferences =
        context.getSharedPreferences(LEGACY_ENCRYPTED_PREFS_NAME, Context.MODE_PRIVATE)
    val previousPlaintextPreferences =
        context.getSharedPreferences(LEGACY_PLAINTEXT_PREFS_NAME, Context.MODE_PRIVATE)
    val previousEncryptedPresent = previousEncryptedPreferences.all.isNotEmpty()

    val clearPreviousEncrypted = {
        check(previousEncryptedPreferences.edit().clear().commit()) {
            "Unable to clear previous encrypted Android session storage"
        }
    }
    val clearPreviousPlaintext = {
        check(previousPlaintextPreferences.edit().clear().commit()) {
            "Unable to clear previous plaintext Android session storage"
        }
    }

    // If the new canonical record already exists (including a logout tombstone), do not even
    // initialize the old cryptographic store. A broken legacy key must never override current data.
    val currentRecord = target.get(SESSION_RECORD_KEY)
    val previousEncrypted =
        if (currentRecord == null && previousEncryptedPresent) {
            try {
                LegacyEncryptedPrefsKeyValueStore(context)
            } catch (_: Exception) {
                // The previous encrypted file exists but is unreadable. Commit a tombstone first;
                // only then may the unreadable encrypted and old plaintext files be removed.
                target.put(
                    SESSION_RECORD_KEY,
                    SessionRecordCodec.encode(StoredSessionRecord(cleared = true)),
                )
                clearPreviousEncrypted()
                clearPreviousPlaintext()
                null
            }
        } else {
            null
        }

    if (currentRecord == null && previousEncryptedPresent && previousEncrypted == null) {
        // The unreadable-store recovery above has already committed a logout tombstone and cleared
        // both old stores. Do not try a plaintext fallback in this launch.
        return AtomicSessionRecordStore(target, androidLegacySessionKeys())
    }

    try {
        AndroidSessionStorageMigration.migrate(
            target = target,
            previousEncrypted = previousEncrypted,
            previousEncryptedStorePresent = previousEncryptedPresent,
            previousPlaintext = PlainPreferencesKeyValueStore(previousPlaintextPreferences),
            legacyKeys = androidLegacySessionKeys(),
            clearPreviousEncrypted = clearPreviousEncrypted,
            clearPreviousPlaintext = clearPreviousPlaintext,
        )
    } catch (_: LegacyAndroidEncryptedStoreReadException) {
        // An old encrypted value failed to decrypt. Fail closed instead of importing plaintext
        // tokens; the tombstone is durable before either legacy file is touched.
        target.put(
            SESSION_RECORD_KEY,
            SessionRecordCodec.encode(StoredSessionRecord(cleared = true)),
        )
        clearPreviousEncrypted()
        clearPreviousPlaintext()
    }

    return AtomicSessionRecordStore(target, androidLegacySessionKeys())
}

private fun androidLegacySessionKeys() =
    LegacySessionKeys(
        accessToken = LEGACY_KEY_ACCESS,
        refreshToken = LEGACY_KEY_REFRESH,
        email = LEGACY_KEY_EMAIL,
    )

/** Current storage: each preference value is independently AES-GCM protected by Android Keystore. */
private class AndroidKeystoreSessionKeyValueStore(
    context: Context,
) : SessionKeyValueStore {
    private val prefs =
        context.getSharedPreferences(KEYSTORE_PREFS_NAME, Context.MODE_PRIVATE)

    override fun get(key: String): String? {
        val encrypted =
            try {
                prefs.getString(key, null) ?: return null
            } catch (exception: Exception) {
                return invalidValueOrThrow(key, exception)
            }
        return try {
            AndroidKeystoreValueCipher.decrypt(key, encrypted)
        } catch (exception: Exception) {
            invalidValueOrThrow(key, exception)
        }
    }

    private fun invalidValueOrThrow(
        key: String,
        cause: Exception,
    ): String {
        if (key == SESSION_RECORD_KEY) {
            // Return a non-null invalid record so AtomicSessionRecordStore invalidates it as a
            // tombstone. Returning null here could make a legacy plaintext session win.
            return CORRUPT_SESSION_RECORD_MARKER
        }
        throw IllegalStateException("Unable to read Android session storage", cause)
    }

    override fun put(
        key: String,
        value: String,
    ) {
        val encrypted = AndroidKeystoreValueCipher.encrypt(key, value)
        check(prefs.edit().putString(key, encrypted).commit()) {
            "Unable to persist Android session storage"
        }
    }

    override fun remove(key: String) {
        check(prefs.edit().remove(key).commit()) {
            "Unable to remove Android session storage entry"
        }
    }
}

/** Read-only adapter for the previous EncryptedSharedPreferences format. */
private class LegacyEncryptedPrefsKeyValueStore(
    context: Context,
) : SessionKeyValueStore {
    private val prefs = createLegacyEncryptedPreferences(context)

    override fun get(key: String): String? =
        try {
            prefs.getString(key, null)?.ifBlank { null }
        } catch (exception: Exception) {
            throw LegacyAndroidEncryptedStoreReadException(exception)
        }

    override fun put(
        key: String,
        value: String,
    ): Unit = error("The previous encrypted Android session store is read-only")

    override fun remove(key: String): Unit = error("The previous encrypted Android session store is read-only")

    private companion object {
        fun createLegacyEncryptedPreferences(context: Context): SharedPreferences {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            return EncryptedSharedPreferences.create(
                LEGACY_ENCRYPTED_PREFS_NAME,
                masterKeyAlias,
                context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }
}

private class PlainPreferencesKeyValueStore(
    private val prefs: SharedPreferences,
) : SessionKeyValueStore {
    override fun get(key: String): String? =
        try {
            prefs.getString(key, null)?.ifBlank { null }
        } catch (exception: ClassCastException) {
            throw IllegalStateException("Previous plaintext Android session data has an invalid type", exception)
        }

    override fun put(
        key: String,
        value: String,
    ): Unit = error("Previous plaintext Android session storage is read-only")

    override fun remove(key: String): Unit = error("Previous plaintext Android session storage is read-only")
}

/**
 * Encrypts each value with AES-GCM using a non-exportable Android Keystore key.
 * The preference key is authenticated as AAD to prevent ciphertext swapping between fields.
 */
private object AndroidKeystoreValueCipher {
    private const val KEY_ALIAS = "com.kenlikdev.qmarket.session.aes.v1"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val PREFIX = "qk1:"
    private const val IV_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    @Synchronized
    fun encrypt(
        preferenceKey: String,
        plaintext: String,
    ): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(create = true))
        cipher.updateAAD(preferenceKey.toByteArray(StandardCharsets.UTF_8))
        val iv = requireNotNull(cipher.iv)
        check(iv.size == IV_LENGTH_BYTES) { "Unexpected Android Keystore AES-GCM IV length" }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
        val payload = iv + ciphertext
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    @Synchronized
    fun decrypt(
        preferenceKey: String,
        encoded: String,
    ): String {
        require(encoded.startsWith(PREFIX)) { "Unknown Android session storage format" }
        val payload = Base64.decode(encoded.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > IV_LENGTH_BYTES + TAG_LENGTH_BITS / 8) {
            "Android session ciphertext is truncated"
        }
        val iv = payload.copyOfRange(0, IV_LENGTH_BYTES)
        val ciphertext = payload.copyOfRange(IV_LENGTH_BYTES, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(create = false),
            GCMParameterSpec(TAG_LENGTH_BITS, iv),
        )
        cipher.updateAAD(preferenceKey.toByteArray(StandardCharsets.UTF_8))
        return cipher.doFinal(ciphertext).toString(StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(create: Boolean): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        check(create) { "Android Keystore session key is unavailable" }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }
}

private const val KEYSTORE_PREFS_NAME = "qmarket_session_keystore_v1"
private const val LEGACY_ENCRYPTED_PREFS_NAME = "qmarket_session_secure"
private const val LEGACY_PLAINTEXT_PREFS_NAME = "qmarket_session"
private const val LEGACY_KEY_ACCESS = "accessToken"
private const val LEGACY_KEY_REFRESH = "refreshToken"
private const val LEGACY_KEY_EMAIL = "email"
private const val CORRUPT_SESSION_RECORD_MARKER = "__qmarket_corrupt_android_session_record_v1__"

actual fun createPlatformSessionStore(): SessionStore {
    val context = androidAppContext
    return if (context != null) {
        AndroidKeystoreSessionStore(context)
    } else {
        InMemorySessionStore()
    }
}
