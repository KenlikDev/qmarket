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
}
