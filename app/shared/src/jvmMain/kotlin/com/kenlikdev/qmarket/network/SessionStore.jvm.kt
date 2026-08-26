package com.kenlikdev.qmarket.network

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/**
 * Desktop/JVM: tokens in `~/.qmarket/session.properties` (user-private file).
 */
class FileSessionStore(
    private val file: Path =
        Path.of(System.getProperty("user.home"), ".qmarket", "session.properties"),
) : SessionStore {
    private val props = Properties()

    init {
        if (Files.isRegularFile(file)) {
            Files.newInputStream(file).use { props.load(it) }
        }
    }

    override fun readAccessToken(): String? = props.getProperty(KEY_ACCESS)?.ifBlank { null }

    override fun readRefreshToken(): String? = props.getProperty(KEY_REFRESH)?.ifBlank { null }

    override fun readEmail(): String? = props.getProperty(KEY_EMAIL)?.ifBlank { null }

    override fun write(
        accessToken: String,
        refreshToken: String,
        email: String?,
    ) {
        props.setProperty(KEY_ACCESS, accessToken)
        props.setProperty(KEY_REFRESH, refreshToken)
        if (email != null) {
            props.setProperty(KEY_EMAIL, email)
        } else {
            props.remove(KEY_EMAIL)
        }
        persist()
    }

    override fun clear() {
        props.clear()
        if (Files.isRegularFile(file)) {
            Files.deleteIfExists(file)
        }
    }

    private fun persist() {
        Files.createDirectories(file.parent)
        Files.newOutputStream(file).use { out ->
            props.store(out, "QMarket session — do not share")
        }
    }

    private companion object {
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_EMAIL = "email"
    }
}

actual fun createPlatformSessionStore(): SessionStore = FileSessionStore()
