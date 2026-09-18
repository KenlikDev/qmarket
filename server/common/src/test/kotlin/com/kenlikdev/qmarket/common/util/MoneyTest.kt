package com.kenlikdev.qmarket.common.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {
    @Test
    fun `toMinorUnits converts major units to minor`() {
        assertEquals(1050L, Money.toMinorUnits(BigDecimal("10.50")))
        assertEquals(1L, Money.toMinorUnits(BigDecimal("0.01")))
        assertEquals(0L, Money.toMinorUnits(BigDecimal("0.00")))
        assertEquals(100L, Money.toMinorUnits(BigDecimal("1")))
    }

    @Test
    fun `toMinorUnits rounds half up to two decimals`() {
        assertEquals(106L, Money.toMinorUnits(BigDecimal("1.055")))
        assertEquals(105L, Money.toMinorUnits(BigDecimal("1.054")))
    }
}
