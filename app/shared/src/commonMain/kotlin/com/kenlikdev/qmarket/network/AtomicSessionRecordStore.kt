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
    fun hasStoredData(): Boolean =
        !accessToken.isNullOrBlank() ||
            !refreshToken.isNullOrBlank() ||
            !email.isNullOrBlank()
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
        removeLegacyKeys(failOnError = true)
    }

    private fun readRecord(): StoredSessionRecord {
        val stored = storage.get(recordKey)
        if (stored != null) {
            val record = SessionRecordCodec.decode(stored)
            removeLegacyKeys(failOnError = false)
            return record
        }

        val legacy =
            StoredSessionRecord(
                accessToken = storage.get(legacyKeys.accessToken)?.ifBlank { null },
                refreshToken = storage.get(legacyKeys.refreshToken)?.ifBlank { null },
                email = storage.get(legacyKeys.email)?.ifBlank { null },
            )
        if (!legacy.hasStoredData()) return legacy

        // Migrate only after the complete legacy tuple has been read. If writing the new record
        // fails, keep legacy data untouched and surface the persistence error.
        storage.put(recordKey, SessionRecordCodec.encode(legacy))
        removeLegacyKeys(failOnError = false)
        return legacy
    }

    private fun removeLegacyKeys(failOnError: Boolean) {
        var firstFailure: Exception? = null
        legacyKeys.all().filter { it != recordKey }.forEach { key ->
            try {
                storage.remove(key)
            } catch (exception: Exception) {
                if (firstFailure == null) firstFailure = exception
            }
        }

        if (failOnError && firstFailure != null) {
            throw IllegalStateException(
                "Session was invalidated, but one or more legacy session entries could not be removed",
                firstFailure,
            )
        }
    }

}
