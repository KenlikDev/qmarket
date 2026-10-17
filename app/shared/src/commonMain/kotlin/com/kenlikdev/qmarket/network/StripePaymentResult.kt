package com.kenlikdev.qmarket.network

/**
 * A platform payment SDK outcome, or a recovery signal emitted when the platform host
 * has to reconcile an interrupted payment flow after process recreation.
 *
 * [COMPLETED] means the SDK flow completed, not that the order is paid. The app must reload the
 * order and wait for the server's verified provider webhook before treating it as PAID.
 * [RECOVERY_REQUIRED] means there is no reliable SDK result; only refresh the order from the server
 * and explain its current status without claiming that the SDK completed, failed, or canceled.
 */
enum class StripePaymentResult {
    COMPLETED,
    CANCELED,
    FAILED,
    RECOVERY_REQUIRED,
}

/**
 * A one-shot platform result event. It contains only an order id and outcome—never a client secret.
 * [eventId] lets the platform host acknowledge exactly the event it delivered.
 */
data class StripePaymentResultEvent(
    val eventId: Long,
    val orderId: String,
    val result: StripePaymentResult,
)
