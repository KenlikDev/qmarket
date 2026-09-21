package com.kenlikdev.qmarket.common.validation

import com.kenlikdev.qmarket.common.exception.BadRequestException

/**
 * E-commerce style input rules (shared by AuthService / ProfileService).
 */
object InputValidation {
    private val namePattern =
        Regex("""^[\p{L}\p{M}]+(?:[ '\-.][\p{L}\p{M}]+)*$""")

    fun normalizeOptionalName(
        raw: String?,
        field: String,
    ): String? {
        if (raw == null) return null
        val value = raw.trim().replace(Regex("\\s+"), " ")
        if (value.isEmpty()) return null
        if (value.length > 100) {
            throw BadRequestException("$field must be at most 100 characters")
        }
        if (value.all { it.isDigit() || it.isWhitespace() }) {
            throw BadRequestException("$field cannot be only digits")
        }
        if (!namePattern.matches(value)) {
            throw BadRequestException(
                "$field may contain letters, spaces, hyphens, apostrophes and periods only",
            )
        }
        return value
    }

    fun requireTrimmedNotBlank(
        raw: String,
        field: String,
    ): String =
        raw.trim().ifEmpty {
            throw BadRequestException("$field must not be blank")
        }

    fun normalizeOptionalPhone(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val hasPlus = trimmed.startsWith('+')
        val body = trimmed.removePrefix("+")
        if ('+' in body) {
            throw BadRequestException("Phone may contain only one leading +")
        }
        if (body.any { !it.isDigit() && !it.isWhitespace() && it !in "()-." }) {
            throw BadRequestException("Phone contains unsupported characters")
        }

        val digits = body.filter(Char::isDigit)
        if (digits.length !in 7..15) {
            throw BadRequestException("Phone must contain 7–15 digits (optional leading +)")
        }
        return if (hasPlus) "+$digits" else digits
    }
}
