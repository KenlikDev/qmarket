package com.kenlikdev.qmarket.common.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Currency helpers shared across order/payment modules.
 * Assumes 2 decimal places (ISO currencies such as RUB, USD, EUR).
 */
object Money {
    /**
     * Convert a major-unit amount to minor units (e.g. rubles → kopecks, dollars → cents)
     * using half-up rounding to 2 fractional digits.
     */
    fun toMinorUnits(amount: BigDecimal): Long =
        amount
            .setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2)
            .longValueExact()
}
