package com.kenlikdev.qmarket.validation

object ClientInputValidation {
    fun isValidEmail(value: String): Boolean {
        val v = value.trim()
        val at = v.indexOf('@')
        return at > 0 &&
            at == v.lastIndexOf('@') &&
            at < v.lastIndex &&
            v.substring(at + 1).contains('.') &&
            !v.substring(at + 1).startsWith('.') &&
            !v.substring(at + 1).endsWith('.')
    }

    fun isValidPhoneInput(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return true

        val hasPlus = trimmed.startsWith('+')
        val body = trimmed.removePrefix("+")
        if ('+' in body) return false

        val digits =
            body.filter { it.isDigit() }
        if (digits.length !in 7..15) return false

        return body.all {
            it.isDigit() || it.isWhitespace() || it == '-' || it == '(' || it == ')' || it == '.'
        } && (!hasPlus || trimmed.length > 1)
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

    fun filterPhoneInput(input: String): String {
        val allowed = { ch: Char ->
            ch.isDigit() ||
                ch.isWhitespace() ||
                ch == '-' ||
                ch == '(' ||
                ch == ')' ||
                ch == '.'
        }
        val leadingWhitespace = input.takeWhile(Char::isWhitespace)
        val body = input.drop(leadingWhitespace.length)
        val hasLeadingPlus = body.startsWith('+')
        val sanitized = body.drop(if (hasLeadingPlus) 1 else 0).filter(allowed)
        return leadingWhitespace + (if (hasLeadingPlus) "+" else "") + sanitized
    }
}
