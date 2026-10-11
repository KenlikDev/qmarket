package com.kenlikdev.qmarket.network

import com.kenlikdev.qmarket.api.AuthResponseDto
import com.kenlikdev.qmarket.api.UserDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtomicSessionRecordStoreTest {
    private val legacyKeys =
        LegacySessionKeys(
            accessToken = "old.access",
            refreshToken = "old.refresh",
            email = "old.email",
        )

    @Test
    fun writeAndRestartUseOneCanonicalRecord() {
        val storage = FakeSessionKeyValueStore()
        val store = AtomicSessionRecordStore(storage, legacyKeys)

        store.write("access-1", "refresh-1", "user@qmarket.test")

        assertEquals(setOf(SESSION_RECORD_KEY), storage.entries.keys)
        val restarted = AtomicSessionRecordStore(storage, legacyKeys)
        assertEquals("access-1", restarted.readAccessToken())
        assertEquals("refresh-1", restarted.readRefreshToken())
        assertEquals("user@qmarket.test", restarted.readEmail())
    }

    @Test
    fun migratesOldSplitKeysIntoTheCanonicalRecord() {
        val storage =
            FakeSessionKeyValueStore(
                mapOf(
                    legacyKeys.accessToken to "legacy-access",
                    legacyKeys.refreshToken to "legacy-refresh",
                    legacyKeys.email to "legacy@qmarket.test",
                ),
            )
        val store = AtomicSessionRecordStore(storage, legacyKeys)

        assertEquals("legacy-access", store.readAccessToken())
        assertEquals("legacy-refresh", store.readRefreshToken())
        assertEquals("legacy@qmarket.test", store.readEmail())
        assertTrue(SESSION_RECORD_KEY in storage.entries)
        legacyKeys.all().forEach { key -> assertFalse(key in storage.entries) }
    }

    @Test
    fun logoutTombstonePreventsResurrectionAfterPartialLegacyCleanupFailure() {
        val storage = FakeSessionKeyValueStore()
        val store = AtomicSessionRecordStore(storage, legacyKeys)
        store.write("active-access", "active-refresh", "user@qmarket.test")

        // Simulate stale keys left by an older version or an interrupted migration.
        storage.entries[legacyKeys.accessToken] = "stale-access"
        storage.entries[legacyKeys.refreshToken] = "stale-refresh"
        storage.entries[legacyKeys.email] = "stale@qmarket.test"
        storage.failRemoveKeys += legacyKeys.refreshToken

        assertFailsWith<IllegalStateException> {
            store.clear()
        }
        assertTrue(SESSION_RECORD_KEY in storage.entries)

        // A fresh provider sees the committed tombstone, even while one legacy key remains.
        val restarted = AtomicSessionRecordStore(storage, legacyKeys)
        assertNull(restarted.readAccessToken())
        assertNull(restarted.readRefreshToken())
        assertNull(restarted.readEmail())
        assertEquals("stale-refresh", storage.entries[legacyKeys.refreshToken])
    }

    @Test
    fun committedTombstoneClearsMemoryEvenWhenLegacyCleanupFails() {
        val storage = FakeSessionKeyValueStore()
        val store = AtomicSessionRecordStore(storage, legacyKeys)
        val provider = MutableTokenProvider(store)
        provider.applyAuth(
            AuthResponseDto(
                accessToken = "active-access",
                refreshToken = "active-refresh",
                expiresIn = 60,
                user = UserDto(id = "u1", email = "active@qmarket.test"),
            ),
        )

        // Model leftover entries that the current app no longer trusts, but must clean up.
        storage.entries[legacyKeys.accessToken] = "stale-access"
        storage.entries[legacyKeys.refreshToken] = "stale-refresh"
        storage.entries[legacyKeys.email] = "stale@qmarket.test"
        storage.failRemoveKeys += legacyKeys.refreshToken

        assertFailsWith<SessionClearedWithCleanupFailure> {
            provider.clear()
        }

        assertNull(provider.accessToken())
        assertNull(provider.refreshToken())
        assertNull(provider.sessionEmail())
        assertFalse(provider.hasSession())

        // A newly created store after process restart still obeys the committed tombstone.
        val restarted = MutableTokenProvider(AtomicSessionRecordStore(storage, legacyKeys))
        assertNull(restarted.accessToken())
        assertNull(restarted.refreshToken())
        assertNull(restarted.sessionEmail())
        assertFalse(restarted.hasSession())
    }

    @Test
    fun failedCanonicalWriteLeavesPreviouslyCommittedSessionIntact() {
        val storage = FakeSessionKeyValueStore()
        val store = AtomicSessionRecordStore(storage, legacyKeys)
        store.write("old-access", "old-refresh", "old@qmarket.test")
        val previousRecord = storage.entries[SESSION_RECORD_KEY]
        storage.failPutKeys += SESSION_RECORD_KEY

        assertFailsWith<IllegalStateException> {
            store.write("new-access", "new-refresh", "new@qmarket.test")
        }

        assertEquals(previousRecord, storage.entries[SESSION_RECORD_KEY])
        assertEquals("old-access", store.readAccessToken())
        assertEquals("old-refresh", store.readRefreshToken())
        assertEquals("old@qmarket.test", store.readEmail())
    }

    @Test
    fun malformedCanonicalRecordIsInvalidatedInsteadOfFallingBackToLegacyTokens() {
        val storage =
            FakeSessionKeyValueStore(
                mapOf(
                    SESSION_RECORD_KEY to
                        """{"version":1,"cleared":false,"accessToken":42,"refreshToken":"bad-refresh","email":"old@qmarket.test"}""",
                    legacyKeys.accessToken to "legacy-access",
                    legacyKeys.refreshToken to "legacy-refresh",
                    legacyKeys.email to "legacy@qmarket.test",
                ),
            )

        val store = AtomicSessionRecordStore(storage, legacyKeys)

        assertNull(store.readAccessToken())
        assertNull(store.readRefreshToken())
        assertNull(store.readEmail())
        assertTrue(SessionRecordCodec.decode(requireNotNull(storage.entries[SESSION_RECORD_KEY])).cleared)
        legacyKeys.all().forEach { key -> assertFalse(key in storage.entries) }
    }

    @Test
    fun emailOnlyLegacyValuesAreRemovedAndNeverPromotedToASession() {
        val storage =
            FakeSessionKeyValueStore(
                mapOf(legacyKeys.email to "stale@qmarket.test"),
            )

        val store = AtomicSessionRecordStore(storage, legacyKeys)

        assertNull(store.readAccessToken())
        assertNull(store.readRefreshToken())
        assertNull(store.readEmail())
        assertFalse(SESSION_RECORD_KEY in storage.entries)
        legacyKeys.all().forEach { key -> assertFalse(key in storage.entries) }
    }

    private class FakeSessionKeyValueStore(
        initial: Map<String, String> = emptyMap(),
    ) : SessionKeyValueStore {
        val entries: MutableMap<String, String> = initial.toMutableMap()
        val failPutKeys = mutableSetOf<String>()
        val failRemoveKeys = mutableSetOf<String>()

        override fun get(key: String): String? = entries[key]

        override fun put(
            key: String,
            value: String,
        ) {
            check(key !in failPutKeys) { "simulated write failure for $key" }
            entries[key] = value
        }

        override fun remove(key: String) {
            check(key !in failRemoveKeys) { "simulated remove failure for $key" }
            entries.remove(key)
        }
    }
}
