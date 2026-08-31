package com.kenlikdev.qmarket.ui

import kotlin.random.Random

/**
 * Client-side Idempotency-Key for POST /orders.
 * Reuse the same key for retries of the same logical checkout until success or the cart/address changes.
 */
object CheckoutIdempotency {
    private const val KEY_LEN = 32
    private const val HEX = "0123456789abcdef"

    fun newKey(random: Random = Random.Default): String =
        buildString(KEY_LEN) {
            repeat(KEY_LEN) {
                append(HEX[random.nextInt(HEX.length)])
            }
        }

    fun isValidKey(key: String?): Boolean {
        if (key == null) return false
        val t = key.trim()
        return t.length in 8..128 && t.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}
