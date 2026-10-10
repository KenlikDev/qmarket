package com.kenlikdev.qmarket.order.payment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class StripeAmountConverterTest {
    @Test
    fun `toMinorUnits converts two decimal currency amounts exactly`() {
        assertEquals(1050L, StripeAmountConverter.toMinorUnits(BigDecimal("10.50"), "USD"))
        assertEquals(1L, StripeAmountConverter.toMinorUnits(BigDecimal("0.01"), "rub"))
        assertEquals(123L, StripeAmountConverter.toMinorUnits(BigDecimal("1.230"), "EUR"))
    }

    @Test
    fun `toMinorUnits does not multiply zero decimal currency amounts by one hundred`() {
        assertEquals(500L, StripeAmountConverter.toMinorUnits(BigDecimal("500.00"), "JPY"))
        assertEquals(125L, StripeAmountConverter.toMinorUnits(BigDecimal("125"), "KRW"))
        assertEquals(0L, StripeAmountConverter.toMinorUnits(BigDecimal("0.00"), "VND"))
    }

    @Test
    fun `toMinorUnits preserves Stripe legacy ISK and UGX representations`() {
        assertEquals(500L, StripeAmountConverter.toMinorUnits(BigDecimal("5.00"), "ISK"))
        assertEquals(700L, StripeAmountConverter.toMinorUnits(BigDecimal("7"), "UGX"))
    }

    @Test
    fun `toMinorUnits rejects amounts that cannot be represented exactly`() {
        assertThrows(IllegalArgumentException::class.java) {
            StripeAmountConverter.toMinorUnits(BigDecimal("1.055"), "USD")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StripeAmountConverter.toMinorUnits(BigDecimal("500.50"), "JPY")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StripeAmountConverter.toMinorUnits(BigDecimal("5.25"), "ISK")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StripeAmountConverter.toMinorUnits(BigDecimal("-1.00"), "USD")
        }
    }

    @Test
    fun `toMinorUnits rejects malformed currency and overflow`() {
        assertThrows(IllegalArgumentException::class.java) {
            StripeAmountConverter.toMinorUnits(BigDecimal("1.00"), "US")
        }
        assertThrows(IllegalArgumentException::class.java) {
            StripeAmountConverter.toMinorUnits(BigDecimal("9223372036854775808"), "USD")
        }
    }
}
