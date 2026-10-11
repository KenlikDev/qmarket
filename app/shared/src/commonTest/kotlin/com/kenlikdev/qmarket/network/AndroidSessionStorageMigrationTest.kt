package com.kenlikdev.qmarket.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidSessionStorageMigrationTest {
    private val keys =
        LegacySessionKeys(
            accessToken = "accessToken",
            refreshToken = "refreshToken",
            email = "email",
        )

    @Test
    fun currentCanonicalRecordWinsAndOldStoresAreCleared() {
        val target = store(record("current-a", "current-r", "current@qmarket.test"))
        val encrypted = store(record("old-encrypted-a", "old-encrypted-r", "old-encrypted@qmarket.test"))
        val plaintext = store(
            mapOf(
                keys.accessToken to "old-plain-a",
                keys.refreshToken to "old-plain-r",
            ),
        )
        var encryptedCleared = false
        var plaintextCleared = false

        val source = migrate(
            target = target,
            encrypted = encrypted,
            encryptedPresent = true,
            plaintext = plaintext,
            clearEncrypted = { encrypted.entries.clear(); encryptedCleared = true },
            clearPlaintext = { plaintext.entries.clear(); plaintextCleared = true },
        )

        assertEquals(AndroidSessionMigrationSource.CURRENT_KEYSTORE_RECORD, source)
        assertEquals(
            StoredSessionRecord("current-a", "current-r", "current@qmarket.test"),
            SessionRecordCodec.decode(requireNotNull(target.entries[SESSION_RECORD_KEY])),
        )
        assertTrue(encryptedCleared)
        assertTrue(plaintextCleared)
        assertTrue(encrypted.entries.isEmpty())
        assertTrue(plaintext.entries.isEmpty())
    }

    @Test
    fun existingLogoutTombstoneCannotBeOverwrittenByLegacyTokens() {
        val target = store(
            SessionRecordCodec.encode(StoredSessionRecord(cleared = true)),
        )
        val encrypted = store(
            mapOf(
                keys.accessToken to "old-encrypted-a",
                keys.refreshToken to "old-encrypted-r",
            ),
        )
        val plaintext = store(
            mapOf(
                keys.accessToken to "old-plain-a",
                keys.refreshToken to "old-plain-r",
            ),
        )

        val source = migrate(
            target = target,
            encrypted = encrypted,
            encryptedPresent = true,
            plaintext = plaintext,
            clearEncrypted = { encrypted.entries.clear() },
            clearPlaintext = { plaintext.entries.clear() },
        )

        assertEquals(AndroidSessionMigrationSource.CURRENT_KEYSTORE_RECORD, source)
        assertTrue(SessionRecordCodec.decode(requireNotNull(target.entries[SESSION_RECORD_KEY])).cleared)
        assertTrue(encrypted.entries.isEmpty())
        assertTrue(plaintext.entries.isEmpty())
    }

    @Test
    fun previousEncryptedCanonicalRecordIsCopiedBeforeSourceCleanup() {
        val target = store()
        val previousRecord = record("encrypted-a", "encrypted-r", "encrypted@qmarket.test")
        val encrypted = store(mapOf(SESSION_RECORD_KEY to previousRecord))
        val plaintext = store(mapOf(keys.refreshToken to "stale-plain-refresh"))
        var encryptedCleared = false

        val source = migrate(
            target = target,
            encrypted = encrypted,
            encryptedPresent = true,
            plaintext = plaintext,
            clearEncrypted = { encrypted.entries.clear(); encryptedCleared = true },
            clearPlaintext = { plaintext.entries.clear() },
        )

        assertEquals(AndroidSessionMigrationSource.PREVIOUS_ENCRYPTED_RECORD, source)
        assertEquals(previousRecord, target.entries[SESSION_RECORD_KEY])
        assertTrue(encryptedCleared)
        assertTrue(encrypted.entries.isEmpty())
        assertTrue(plaintext.entries.isEmpty())
    }

    @Test
    fun previousEncryptedSplitKeysBecomeOneCanonicalRecord() {
        val target = store()
        val encrypted = store(
            mapOf(
                keys.accessToken to "encrypted-a",
                keys.refreshToken to "encrypted-r",
                keys.email to "encrypted@qmarket.test",
            ),
        )
        val plaintext = store(mapOf(keys.refreshToken to "stale-plain-refresh"))

        val source = migrate(
            target = target,
            encrypted = encrypted,
            encryptedPresent = true,
            plaintext = plaintext,
            clearEncrypted = { encrypted.entries.clear() },
            clearPlaintext = { plaintext.entries.clear() },
        )

        assertEquals(AndroidSessionMigrationSource.PREVIOUS_ENCRYPTED_KEYS, source)
        assertEquals(
            StoredSessionRecord("encrypted-a", "encrypted-r", "encrypted@qmarket.test"),
            SessionRecordCodec.decode(requireNotNull(target.entries[SESSION_RECORD_KEY])),
        )
        assertTrue(encrypted.entries.isEmpty())
        assertTrue(plaintext.entries.isEmpty())
    }

    @Test
    fun plaintextKeysAreImportedOnlyWhenPreviousEncryptedStoreIsAbsent() {
        val target = store()
        val plaintext = store(
            mapOf(
                keys.accessToken to "legacy-a",
                keys.refreshToken to "legacy-r",
                keys.email to "legacy@qmarket.test",
            ),
        )

        val source = migrate(
            target = target,
            encrypted = null,
            encryptedPresent = false,
            plaintext = plaintext,
            clearEncrypted = {},
            clearPlaintext = { plaintext.entries.clear() },
        )

        assertEquals(AndroidSessionMigrationSource.PREVIOUS_PLAINTEXT_KEYS, source)
        assertEquals(
            StoredSessionRecord("legacy-a", "legacy-r", "legacy@qmarket.test"),
            SessionRecordCodec.decode(requireNotNull(target.entries[SESSION_RECORD_KEY])),
        )
        assertTrue(plaintext.entries.isEmpty())
    }

    @Test
    fun nonEmptyPreviousEncryptedStorePreventsFallbackToPlaintext() {
        val target = store()
        val encrypted = store(mapOf("obsolete-entry" to "not-a-session"))
        val plaintext = store(
            mapOf(
                keys.accessToken to "stale-plain-a",
                keys.refreshToken to "stale-plain-r",
            ),
        )

        val source = migrate(
            target = target,
            encrypted = encrypted,
            encryptedPresent = true,
            plaintext = plaintext,
            clearEncrypted = { encrypted.entries.clear() },
            clearPlaintext = { plaintext.entries.clear() },
        )

        assertEquals(AndroidSessionMigrationSource.EMPTY, source)
        assertFalse(SESSION_RECORD_KEY in target.entries)
        assertTrue(encrypted.entries.isEmpty())
        assertTrue(plaintext.entries.isEmpty())
    }

    @Test
    fun failedCanonicalWriteLeavesOldStoresForRetry() {
        val target = FakeStore(failCanonicalWrite = true)
        val plaintext = store(
            mapOf(
                keys.accessToken to "legacy-a",
                keys.refreshToken to "legacy-r",
            ),
        )
        var cleanupAttempted = false

        assertFailsWith<IllegalStateException> {
            migrate(
                target = target,
                encrypted = null,
                encryptedPresent = false,
                plaintext = plaintext,
                clearEncrypted = {},
                clearPlaintext = {
                    cleanupAttempted = true
                    plaintext.entries.clear()
                },
            )
        }

        assertFalse(cleanupAttempted)
        assertEquals("legacy-a", plaintext.entries[keys.accessToken])
        assertEquals("legacy-r", plaintext.entries[keys.refreshToken])
        assertTrue(target.entries.isEmpty())
    }

    private fun migrate(
        target: SessionKeyValueStore,
        encrypted: SessionKeyValueStore?,
        encryptedPresent: Boolean,
        plaintext: SessionKeyValueStore,
        clearEncrypted: () -> Unit,
        clearPlaintext: () -> Unit,
    ): AndroidSessionMigrationSource =
        AndroidSessionStorageMigration.migrate(
            target = target,
            previousEncrypted = encrypted,
            previousEncryptedStorePresent = encryptedPresent,
            previousPlaintext = plaintext,
            legacyKeys = keys,
            clearPreviousEncrypted = clearEncrypted,
            clearPreviousPlaintext = clearPlaintext,
        )

    private fun record(
        accessToken: String,
        refreshToken: String,
        email: String,
    ): String =
        SessionRecordCodec.encode(
            StoredSessionRecord(
                accessToken = accessToken,
                refreshToken = refreshToken,
                email = email,
            ),
        )

    private fun store(
        entries: Map<String, String> = emptyMap(),
    ): FakeStore = FakeStore(entries)

    private class FakeStore(
        entries: Map<String, String> = emptyMap(),
        private val failCanonicalWrite: Boolean = false,
    ) : SessionKeyValueStore {
        val entries = entries.toMutableMap()

        override fun get(key: String): String? = entries[key]

        override fun put(
            key: String,
            value: String,
        ) {
            check(!(failCanonicalWrite && key == SESSION_RECORD_KEY)) {
                "Simulated canonical write failure"
            }
            entries[key] = value
        }

        override fun remove(key: String) {
            entries.remove(key)
        }
    }
}
