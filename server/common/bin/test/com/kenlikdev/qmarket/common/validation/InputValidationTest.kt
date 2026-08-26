package com.kenlikdev.qmarket.common.validation

import com.kenlikdev.qmarket.common.exception.BadRequestException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InputValidationTest {
    @Test
    fun `name accepts international forms`() {
        assertEquals("José", InputValidation.normalizeOptionalName("  José ", "First name"))
        assertEquals("O'Brien", InputValidation.normalizeOptionalName("O'Brien", "Last name"))
        assertEquals("Jean-Pierre", InputValidation.normalizeOptionalName("Jean-Pierre", "First name"))
        assertEquals("Mary Ann", InputValidation.normalizeOptionalName("Mary   Ann", "First name"))
    }

    @Test
    fun `name rejects digits-only and symbols`() {
        assertThrows(BadRequestException::class.java) {
            InputValidation.normalizeOptionalName("12345", "Last name")
        }
        assertThrows(BadRequestException::class.java) {
            InputValidation.normalizeOptionalName("<script>", "First name")
        }
    }

    @Test
    fun `blank name becomes null`() {
        assertNull(InputValidation.normalizeOptionalName("   ", "First name"))
        assertNull(InputValidation.normalizeOptionalName(null, "First name"))
    }

    @Test
    fun `phone normalizes and rejects short`() {
        assertEquals("+79001234567", InputValidation.normalizeOptionalPhone("+7 (900) 123-45-67"))
        assertEquals("79001234567", InputValidation.normalizeOptionalPhone("79001234567"))
        assertNull(InputValidation.normalizeOptionalPhone("  "))
        assertThrows(BadRequestException::class.java) {
            InputValidation.normalizeOptionalPhone("+123")
        }
        assertThrows(BadRequestException::class.java) {
            InputValidation.normalizeOptionalPhone("callme")
        }
    }
}
