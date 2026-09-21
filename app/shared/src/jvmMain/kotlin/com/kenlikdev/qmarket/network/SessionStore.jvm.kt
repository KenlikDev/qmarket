package com.kenlikdev.qmarket.network

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.util.EnumSet
import java.util.Properties

/**
 * Desktop/JVM session store.
 *
 * Tokens are kept in a private user file and written atomically to avoid leaving
 * a partially written credentials file after an interruption.
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
        Files.deleteIfExists(file)
    }

    private fun persist() {
        val parent = file.parent ?: error("Session file must have a parent directory")
        Files.createDirectories(parent)
        restrictDirectoryPermissions(parent)

        val temp =
            Files.createTempFile(parent, ".session-", ".tmp")
        try {
            Files.newOutputStream(temp).use { out ->
                props.store(out, "QMarket session — do not share")
            }
            restrictPermissions(temp)
            try {
                Files.move(
                    temp,
                    file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(
                    temp,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
            restrictPermissions(file)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun restrictDirectoryPermissions(path: Path) {
        try {
            Files.setPosixFilePermissions(
                path,
                EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            // Non-POSIX filesystems use their platform's user permissions.
        }
    }

    private fun restrictPermissions(path: Path) {
        try {
            Files.setPosixFilePermissions(
                path,
                EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            )
        } catch (_: UnsupportedOperationException) {
            // Non-POSIX filesystems use their platform's user permissions.
        }
    }

    private companion object {
        const val KEY_ACCESS = "accessToken"
        const val KEY_REFRESH = "refreshToken"
        const val KEY_EMAIL = "email"
    }
}

actual fun createPlatformSessionStore(): SessionStore = FileSessionStore()
