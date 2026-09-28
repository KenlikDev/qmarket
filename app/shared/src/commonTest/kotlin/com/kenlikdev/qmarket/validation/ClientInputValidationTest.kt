package com.kenlikdev.qmarket.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientInputValidationTest {
    @Test
    fun email() {
        assertTrue(ClientInputValidation.isValidEmail("a@b.c"))
        assertFalse(ClientInputValidation.isValidEmail("not-an-email"))
        assertFalse(ClientInputValidation.isValidEmail("a@b"))
    }

    @Test
    fun password() {
        assertTrue(ClientInputValidation.isValidPassword("password1"))
        assertFalse(ClientInputValidation.isValidPassword("short"))
    }

    @Test
    fun phone() {
        assertTrue(ClientInputValidation.isValidPhoneInput(""))
        assertTrue(ClientInputValidation.isValidPhoneInput("+7 (900) 123-45-67"))
        assertFalse(ClientInputValidation.isValidPhoneInput("abc"))
        assertFalse(ClientInputValidation.isValidPhoneInput("+123"))
        assertEquals(
            "+79001234567",
            ClientInputValidation.filterPhoneInput("+7900abc1234567"),
        )
    }

    @Test
    fun personName() {
        assertTrue(ClientInputValidation.isValidPersonName("José"))
        assertTrue(ClientInputValidation.isValidPersonName("O'Brien"))
        assertTrue(ClientInputValidation.isValidPersonName(""))
        assertFalse(ClientInputValidation.isValidPersonName("12345"))
        assertFalse(ClientInputValidation.isValidPersonName("<script>"))
    }
    @Test
    fun phoneAcceptsFormattedInternationalInput() {
        assertTrue(ClientInputValidation.isValidPhoneInput("+7 (999) 123-45.67"))
    }

    @Test
    fun phoneRejectsInternalPlusAndUnsupportedCharacters() {
        assertFalse(ClientInputValidation.isValidPhoneInput("7+9991234567"))
        assertFalse(ClientInputValidation.isValidPhoneInput("79991234567/"))
    }

    @Test
    fun phoneFilterKeepsOnlyLeadingPlusAndAllowedCharacters() {
        assertEquals(
            "+7 (999) 123-45.67",
            ClientInputValidation.filterPhoneInput("+7 (999) 123-45.67+abc/"),
        )
        assertEquals("79991234567", ClientInputValidation.filterPhoneInput("7999abc12345"))
    }

}
