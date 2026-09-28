package com.kenlikdev.qmarket.identity.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class GoogleOAuthPropertiesTest {
    @Test
    fun enabledOAuthRequiresClientId() {
        assertThrows(IllegalArgumentException::class.java) {
            GoogleOAuthProperties(enabled = true)
        }
    }

    @Test
    fun normalizedClientIdsRemoveBlanksAndTrimValues() {
        val props =
            GoogleOAuthProperties(
                enabled = true,
                clientIds = listOf(" client-a ", "", "client-a"),
            )

        assertEquals(setOf("client-a"), props.normalizedClientIds())
    }
}
