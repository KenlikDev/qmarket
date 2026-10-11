package com.kenlikdev.qmarket.network

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
