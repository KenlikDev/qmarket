package com.kenlikdev.qmarket.network

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionStoreJvmTest {
    @Test
    fun persistedSessionCanBeReopened() {
        val directory = Files.createTempDirectory("qmarket-session-test")
        val file = directory.resolve("session.properties")

        try {
            FileSessionStore(file).write(
                accessToken = "access-token",
                refreshToken = "refresh-token",
                email = "user@example.com",
            )

            val reopened = FileSessionStore(file)

            assertEquals("access-token", reopened.readAccessToken())
            assertEquals("refresh-token", reopened.readRefreshToken())
            assertEquals("user@example.com", reopened.readEmail())
        } finally {
            Files.deleteIfExists(file)
            Files.deleteIfExists(directory)
        }
    }
}
