package com.kenlikdev.qmarket.validation

/**
 * Client-side mirrors of server [com.kenlikdev.qmarket.common.validation.InputValidation]
 * for Compose form enable/disable and instant feedback.
 */
object ClientInputValidation {
    fun isValidEmail(value: String): Boolean {
        val v = value.trim()
        return v.contains("@") && v.substringAfter("@").contains(".")
    }

    fun isValidPhoneInput(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return true
        if (trimmed.any { it.isLetter() }) return false
        val digits = trimmed.filter { it.isDigit() }
        return digits.length in 7..15
    }

    fun isValidPersonName(value: String): Boolean {
        val v = value.trim()
        if (v.isEmpty()) return true
        if (v.length > 100) return false
        if (v.all { it.isDigit() || it.isWhitespace() }) return false
        return v.all { ch ->
            ch.isLetter() || ch.isWhitespace() || ch == '-' || ch == '.' || ch.code == 39
        }
    }

    fun isValidPassword(value: String): Boolean = value.length in 8..100

    /** Filter phone field keystrokes (digits and common separators only). */
    fun filterPhoneInput(input: String): String =
        input.filter { ch ->
            ch.isDigit() ||
                ch == '+' ||
                ch.isWhitespace() ||
                ch == '-' ||
                ch == '(' ||
                ch == ')'
        }
}
