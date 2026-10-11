package com.kenlikdev.qmarket.network

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileSessionStoreTest {
    @Test
    fun clearedSessionDoesNotReturnAfterStoreRecreation() {
        val directory = Files.createTempDirectory("qmarket-session-test")
        val sessionFile = directory.resolve("session.properties")

        try {
            FileSessionStore(sessionFile).write(
                accessToken = "access-before-restart",
                refreshToken = "refresh-before-restart",
                email = "user@qmarket.test",
            )

            if (Files.getFileStore(sessionFile).supportsFileAttributeView("posix")) {
                assertTrue(
                    PosixFilePermission.OWNER_EXECUTE in Files.getPosixFilePermissions(directory),
                    "The private session directory must remain traversable by its owner",
                )
                assertEquals(
                    setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    Files.getPosixFilePermissions(sessionFile),
                )
            }

            val restored = FileSessionStore(sessionFile)
            assertEquals("access-before-restart", restored.readAccessToken())
            assertEquals("refresh-before-restart", restored.readRefreshToken())
            assertEquals("user@qmarket.test", restored.readEmail())

            restored.clear()

            val afterLogout = FileSessionStore(sessionFile)
            assertNull(afterLogout.readAccessToken())
            assertNull(afterLogout.readRefreshToken())
            assertNull(afterLogout.readEmail())
        } finally {
            Files.deleteIfExists(sessionFile)
            Files.deleteIfExists(directory)
        }
    }

    @Test
    fun repairsLegacyDirectoryAndFilePermissionsBeforeReadingSession() {
        val home = Files.createTempDirectory("qmarket-session-home")
        val directory = home.resolve(".qmarket")
        val sessionFile = directory.resolve("session.properties")
        val posixPermissions =
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            )

        try {
            FileSessionStore(sessionFile).write(
                accessToken = "legacy-access",
                refreshToken = "legacy-refresh",
                email = "legacy@qmarket.test",
            )

            if (!Files.getFileStore(sessionFile).supportsFileAttributeView("posix")) {
                return
            }

            // Reproduce older desktop builds: directory missing search/execute permission,
            // and credentials file broader than owner-only. The next app start must recover both.
            Files.setPosixFilePermissions(
                sessionFile,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.OTHERS_READ,
                ),
            )
            Files.setPosixFilePermissions(
                directory,
                setOf(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                ),
            )

            val restored = FileSessionStore(sessionFile)

            assertEquals("legacy-access", restored.readAccessToken())
            assertEquals("legacy-refresh", restored.readRefreshToken())
            assertEquals("legacy@qmarket.test", restored.readEmail())
            assertEquals(posixPermissions, Files.getPosixFilePermissions(directory))
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(sessionFile),
            )
        } finally {
            // Ensure cleanup remains possible even when the regression assertion fails.
            runCatching { Files.setPosixFilePermissions(directory, posixPermissions) }
            Files.deleteIfExists(sessionFile)
            Files.deleteIfExists(directory)
            Files.deleteIfExists(home)
        }
    }

}
