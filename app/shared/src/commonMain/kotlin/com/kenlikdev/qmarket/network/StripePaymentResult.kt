package com.kenlikdev.qmarket.network

/**
 * Outcome reported by a platform payment SDK.
 *
 * [COMPLETED] means the SDK flow completed, not that the order is paid. The app must reload the
 * order and wait for the server's verified provider webhook before treating it as PAID.
 */
enum class StripePaymentResult {
    COMPLETED,
    CANCELED,
    FAILED,
}
