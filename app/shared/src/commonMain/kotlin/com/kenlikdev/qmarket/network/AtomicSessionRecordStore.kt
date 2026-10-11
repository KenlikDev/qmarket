package com.kenlikdev.qmarket.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val SESSION_RECORD_KEY = "qmarket.session.v2"

/**
 * The durable tombstone was committed, so the session is cleared even though obsolete legacy
 * entries could not all be removed. Callers must clear their in-memory session, then surface this
 * cleanup warning without treating logout as uncommitted.
 */
internal class SessionClearedWithCleanupFailure(
    cause: Throwable,
) : IllegalStateException(
        "The session was cleared, but legacy session storage could not be fully cleaned",
        cause,
    )

/**
 * Single-key persistence adapter. Each platform maps one key write to an atomic native operation:
 * localStorage.setItem, one Keychain item update, or one committed SharedPreferences editor.
 */
internal interface SessionKeyValueStore {
    fun get(key: String): String?

    fun put(
        key: String,
        value: String,
    )

    fun remove(key: String)
}

/** Names written by older app versions; kept only to migrate existing installed sessions. */
internal data class LegacySessionKeys(
    val accessToken: String,
    val refreshToken: String,
    val email: String,
) {
    fun all(): List<String> = listOf(accessToken, refreshToken, email).distinct()
}

/**
 * The one persisted value for a session. A tombstone is written on logout before legacy keys
 * are removed; that way an interrupted cleanup cannot cause a later launch to revive old tokens.
 */
internal data class StoredSessionRecord(
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val email: String? = null,
    val cleared: Boolean = false,
) {
    fun hasCredentials(): Boolean =
        !accessToken.isNullOrBlank() || !refreshToken.isNullOrBlank()
}

internal object SessionRecordCodec {
    private const val VERSION = 1

    fun encode(record: StoredSessionRecord): String =
        buildJsonObject {
            put("version", JsonPrimitive(VERSION))
            put("cleared", JsonPrimitive(record.cleared))
            put("accessToken", record.accessToken?.let { JsonPrimitive(it) } ?: JsonNull)
            put("refreshToken", record.refreshToken?.let { JsonPrimitive(it) } ?: JsonNull)
            put("email", record.email?.let { JsonPrimitive(it) } ?: JsonNull)
        }.toString()

    fun decode(value: String): StoredSessionRecord =
        try {
            val root = Json.parseToJsonElement(value).jsonObject
            val version =
                root["version"]?.jsonPrimitive?.intOrNull
                    ?: error("Stored session record has no numeric version")
            check(version == VERSION) { "Unsupported stored session record version: $version" }
            val cleared =
                root["cleared"]?.jsonPrimitive?.booleanOrNull
                    ?: error("Stored session record has no Boolean cleared flag")

            StoredSessionRecord(
                accessToken = root.nullableString("accessToken"),
                refreshToken = root.nullableString("refreshToken"),
                email = root.nullableString("email"),
                cleared = cleared,
            )
        } catch (exception: Exception) {
            throw IllegalStateException("Unable to decode the stored QMarket session", exception)
        }

    private fun JsonObject.nullableString(key: String): String? {
        val element: JsonElement = this[key] ?: return null
        if (element == JsonNull) return null
        val primitive = element.jsonPrimitive
        check(primitive.isString) { "Stored session field '$key' is not a string" }
        return primitive.contentOrNull?.ifBlank { null }
    }
}

/**
 * Stores access token, refresh token and email as one record. Reads old per-field keys only when
 * the new record does not exist. The canonical record (including its logout tombstone) always wins
 * over legacy keys.
 */
internal class AtomicSessionRecordStore(
    private val storage: SessionKeyValueStore,
    private val legacyKeys: LegacySessionKeys,
    private val recordKey: String = SESSION_RECORD_KEY,
) : SessionStore {
    private var legacyKeysCleaned = false

    init {
        // Migrate before the store is handed to the token provider. Lazy migration could race
        // with logout: a reader could load legacy tokens, logout could commit a tombstone, and
        // that reader could then write the legacy session back over the tombstone.
        migrateLegacyRecordIfNeeded()
    }

    override fun readAccessToken(): String? =
        readRecord().takeUnless { it.cleared }?.accessToken?.ifBlank { null }

    override fun readRefreshToken(): String? =
        readRecord().takeUnless { it.cleared }?.refreshToken?.ifBlank { null }

    override fun readEmail(): String? =
        readRecord().takeUnless { it.cleared }?.email?.ifBlank { null }

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) {
        // One native write commits the entire session or leaves the old session intact.
        storage.put(
            recordKey,
            SessionRecordCodec.encode(
                StoredSessionRecord(
                    accessToken = accessToken,
                    refreshToken = refreshToken,
                    email = email,
                ),
            ),
        )
        // The canonical record is already committed; legacy keys are no longer authoritative.
        // Cleanup is best-effort here so a legacy key deletion failure cannot turn a successful
        // login into a half-applied in-memory session.
        removeLegacyKeys(failOnError = false)
    }

    override fun clear() {
        // Commit the tombstone first. If this single write fails, do not touch the previous record.
        // If subsequent legacy cleanup fails, future reads still see the tombstone and fail closed.
        storage.put(recordKey, SessionRecordCodec.encode(StoredSessionRecord(cleared = true)))
        try {
            removeLegacyKeys(failOnError = true)
        } catch (exception: IllegalStateException) {
            throw SessionClearedWithCleanupFailure(exception)
        }
    }

    private fun readRecord(): StoredSessionRecord {
        val stored = storage.get(recordKey) ?: return StoredSessionRecord()
        return decodeOrInvalidate(stored)
    }

    private fun migrateLegacyRecordIfNeeded() {
        val stored = storage.get(recordKey)
        if (stored != null) {
            // Invalid data is invalidated as one record; never fall back to stale split keys.
            decodeOrInvalidate(stored)
            removeLegacyKeys(failOnError = false)
            return
        }

        val legacy =
            StoredSessionRecord(
                accessToken = storage.get(legacyKeys.accessToken)?.ifBlank { null },
                refreshToken = storage.get(legacyKeys.refreshToken)?.ifBlank { null },
                email = storage.get(legacyKeys.email)?.ifBlank { null },
            )
        if (!legacy.hasCredentials()) {
            // No credentials means a legacy email-only record is stale, not a session.
            removeLegacyKeys(failOnError = false)
            return
        }

        // Persist the complete legacy tuple before deleting any old key. A failed write leaves
        // the original session recoverable and propagates the failure to the caller.
        storage.put(recordKey, SessionRecordCodec.encode(legacy))
        removeLegacyKeys(failOnError = false)
    }

    private fun decodeOrInvalidate(stored: String): StoredSessionRecord =
        try {
            SessionRecordCodec.decode(stored)
        } catch (_: IllegalStateException) {
            // A corrupt or unknown-version record must never be replaced with older legacy tokens.
            val tombstone = StoredSessionRecord(cleared = true)
            storage.put(recordKey, SessionRecordCodec.encode(tombstone))
            removeLegacyKeys(failOnError = false)
            tombstone
        }

    private fun removeLegacyKeys(failOnError: Boolean) {
        if (legacyKeysCleaned && !failOnError) return

        var firstFailure: Exception? = null
        legacyKeys.all().filter { it != recordKey }.forEach { key ->
            try {
                storage.remove(key)
            } catch (exception: Exception) {
                if (firstFailure == null) firstFailure = exception
            }
        }

        if (firstFailure == null) {
            legacyKeysCleaned = true
        }

        if (failOnError && firstFailure != null) {
            throw IllegalStateException(
                "Session was invalidated, but one or more legacy session entries could not be removed",
                firstFailure,
            )
        }
    }
}