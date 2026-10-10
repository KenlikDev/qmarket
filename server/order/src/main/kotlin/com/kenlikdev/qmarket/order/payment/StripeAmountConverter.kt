package com.kenlikdev.qmarket.order.payment

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Converts order totals to the integer amount representation required by Stripe's API.
 *
 * Stripe uses two decimals for most currencies, zero decimals for some currencies, and a
 * backwards-compatible two-decimal representation requiring whole units for ISK and UGX.
 * This provider-specific rule intentionally lives in the order/payment module, not common.
 */
object StripeAmountConverter {
    private val zeroDecimalCurrencies =
        setOf(
            "BIF",
            "CLP",
            "DJF",
            "GNF",
            "JPY",
            "KMF",
            "KRW",
            "MGA",
            "PYG",
            "RWF",
            "VND",
            "VUV",
            "XAF",
            "XOF",
            "XPF",
        )

    private val wholeUnitTwoDecimalCurrencies = setOf("ISK", "UGX")

    /**
     * Return Stripe's integer amount for [amount] in [currency].
     *
     * Fractional values that cannot be represented by Stripe are rejected; this function never
     * rounds a charge up or down. The currency code is case-insensitive.
     *
     * @throws IllegalArgumentException if the currency is malformed, amount is negative, or the
     * amount has unsupported precision / overflows Stripe's integer amount representation.
     */
    fun toMinorUnits(
        amount: BigDecimal,
        currency: String,
    ): Long {
        val code = currency.trim().uppercase(Locale.ROOT)
        require(code.matches(Regex("^[A-Z]{3}$"))) {
            "Currency must be a three-letter ISO code"
        }
        require(amount.signum() >= 0) {
            "Payment amount must not be negative"
        }

        return try {
            when {
                code in zeroDecimalCurrencies ->
                    amount.setScale(0, RoundingMode.UNNECESSARY).longValueExact()

                code in wholeUnitTwoDecimalCurrencies ->
                    amount
                        .setScale(0, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact()

                else ->
                    amount
                        .setScale(2, RoundingMode.UNNECESSARY)
                        .movePointRight(2)
                        .longValueExact()
            }
        } catch (ex: ArithmeticException) {
            throw IllegalArgumentException(
                "Amount cannot be represented exactly in Stripe minor units for $code",
                ex,
            )
        }
    }
}
