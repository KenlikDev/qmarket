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

    fun normalizeOptionalPhone(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.any { it.isLetter() }) {
            throw BadRequestException("Phone must contain digits only (optional leading +)")
        }
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length !in 7..15) {
            throw BadRequestException("Phone must contain 7–15 digits (optional leading +)")
        }
        return if (hasPlus) "+$digits" else digits
    }
}
