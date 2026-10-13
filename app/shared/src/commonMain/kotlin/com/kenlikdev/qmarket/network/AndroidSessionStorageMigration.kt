package com.kenlikdev.qmarket.network

/**
 * The outcome is diagnostic only; all stored credentials remain in the canonical session record.
 */
internal enum class AndroidSessionMigrationSource {
    CURRENT_KEYSTORE_RECORD,
    PREVIOUS_ENCRYPTED_RECORD,
    PREVIOUS_ENCRYPTED_KEYS,
    PREVIOUS_PLAINTEXT_KEYS,
    EMPTY,
}

/**
 * Raised when the old EncryptedSharedPreferences source exists but its data cannot be decrypted.
 * The Android adapter must commit a logout tombstone before deleting old sources.
 */
internal class LegacyAndroidEncryptedStoreReadException(
    cause: Throwable,
) : IllegalStateException("Unable to read the previous encrypted Android session store", cause)

/**
 * Migrates old Android session formats to the Keystore-backed key-value store.
 *
 * One canonical record already in [target] always wins, including a logout tombstone. Previous
 * encrypted storage is authoritative when its file has data but no credentials are readable;
 * stale plaintext values must not be allowed to revive a previous session.
 */
internal object AndroidSessionStorageMigration {
    fun migrate(
        target: SessionKeyValueStore,
        previousEncrypted: SessionKeyValueStore?,
        previousEncryptedStorePresent: Boolean,
        previousPlaintext: SessionKeyValueStore,
        legacyKeys: LegacySessionKeys,
        clearPreviousEncrypted: () -> Unit,
        clearPreviousPlaintext: () -> Unit,
    ): AndroidSessionMigrationSource {
        val source =
            when {
                target.get(SESSION_RECORD_KEY) != null ->
                    AndroidSessionMigrationSource.CURRENT_KEYSTORE_RECORD

                else -> {
                    val encryptedRecord = previousEncrypted?.get(SESSION_RECORD_KEY)
                    if (encryptedRecord != null) {
                        target.put(SESSION_RECORD_KEY, encryptedRecord)
                        AndroidSessionMigrationSource.PREVIOUS_ENCRYPTED_RECORD
                    } else {
                        val encryptedLegacy = readLegacyRecord(previousEncrypted, legacyKeys)
                        when {
                            encryptedLegacy.hasCredentials() -> {
                                target.put(
                                    SESSION_RECORD_KEY,
                                    SessionRecordCodec.encode(encryptedLegacy),
                                )
                                AndroidSessionMigrationSource.PREVIOUS_ENCRYPTED_KEYS
                            }

                            previousEncryptedStorePresent ->
                                AndroidSessionMigrationSource.EMPTY

                            else -> {
                                val plaintextLegacy = readLegacyRecord(previousPlaintext, legacyKeys)
                                if (plaintextLegacy.hasCredentials()) {
                                    target.put(
                                        SESSION_RECORD_KEY,
                                        SessionRecordCodec.encode(plaintextLegacy),
                                    )
                                    AndroidSessionMigrationSource.PREVIOUS_PLAINTEXT_KEYS
                                } else {
                                    AndroidSessionMigrationSource.EMPTY
                                }
                            }
                        }
                    }
                }
            }

        // Clean old files only after the canonical Keystore-backed value is safely committed.
        // A cleanup failure leaves the new record authoritative; the next launch retries cleanup.
        clearPreviousEncrypted()
        clearPreviousPlaintext()
        return source
    }

    private fun readLegacyRecord(
        store: SessionKeyValueStore?,
        keys: LegacySessionKeys,
    ): StoredSessionRecord =
        StoredSessionRecord(
            accessToken = store?.get(keys.accessToken)?.ifBlank { null },
            refreshToken = store?.get(keys.refreshToken)?.ifBlank { null },
            email = store?.get(keys.email)?.ifBlank { null },
        )
}
