package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.ui.CheckoutIdempotency
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CheckoutIdempotencyTest {
    @Test
    fun newKeyIs32HexChars() {
        val key = CheckoutIdempotency.newKey(Random(42))
        assertEquals(32, key.length)
        assertTrue(key.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(CheckoutIdempotency.isValidKey(key))
    }

    @Test
    fun differentSeedsDiffer() {
        val a = CheckoutIdempotency.newKey(Random(1))
        val b = CheckoutIdempotency.newKey(Random(2))
        assertNotEquals(a, b)
    }

    @Test
    fun invalidKeysRejected() {
        assertFalse(CheckoutIdempotency.isValidKey(null))
        assertFalse(CheckoutIdempotency.isValidKey(""))
        assertFalse(CheckoutIdempotency.isValidKey("ab"))
        assertFalse(CheckoutIdempotency.isValidKey("!!!invalid!!!"))
    }
}
