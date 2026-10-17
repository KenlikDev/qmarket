package com.kenlikdev.qmarket

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kenlikdev.qmarket.network.StripePaymentResult
import com.kenlikdev.qmarket.network.StripePaymentResultEvent
import platform.Foundation.NSUserDefaults

/**
 * One-shot bridge for native iOS PaymentSheet results.
 *
 * Persist only the non-secret order routing identifier so a process restart during an external
 * authentication step can recover by reloading the order from QMarket. Never persist the
 * PaymentIntent client secret or card/payment details.
 */
class IosStripePaymentHost {
    private val defaults = NSUserDefaults.standardUserDefaults
    private var nextEventId = 1L

    var stripePaymentResult: StripePaymentResultEvent? by mutableStateOf(restorePendingPayment())
        private set

    /** Reserve the single native sheet slot before presenting PaymentSheet. */
    fun beginPayment(orderId: String): Boolean {
        if (orderId.isBlank() || stripePaymentResult != null) return false
        if (!defaults.stringForKey(KEY_PENDING_ORDER_ID).isNullOrBlank()) return false

        defaults.setObject(orderId, forKey = KEY_PENDING_ORDER_ID)
        return true
    }

    /** Publish a SDK outcome so Compose can reload authoritative server state. */
    fun publishPaymentResult(
        orderId: String,
        outcome: String,
    ) {
        val pendingOrderId = defaults.stringForKey(KEY_PENDING_ORDER_ID)
        // A callback is valid only while this order owns the active native sheet slot.
        // Ignore late/duplicate callbacks after the pending order has already been consumed.
        if (pendingOrderId != orderId) return
        if (stripePaymentResult != null) return

        val result =
            when (outcome.lowercase()) {
                "completed" -> StripePaymentResult.COMPLETED
                "canceled", "cancelled" -> StripePaymentResult.CANCELED
                else -> StripePaymentResult.FAILED
            }
        stripePaymentResult =
            StripePaymentResultEvent(
                eventId = nextEventId++,
                orderId = orderId,
                result = result,
            )
    }

    /** Acknowledge only the event the shared UI has finished reconciling against the API. */
    fun consumePaymentResult(eventId: Long) {
        val event = stripePaymentResult ?: return
        if (event.eventId != eventId) return

        stripePaymentResult = null
        if (defaults.stringForKey(KEY_PENDING_ORDER_ID) == event.orderId) {
            defaults.removeObjectForKey(KEY_PENDING_ORDER_ID)
        }
    }

    /**
     * After process recreation, emit a reconciliation event for the pending order. Its status is
     * read from the authenticated API; the restored event never assumes that payment succeeded.
     */
    private fun restorePendingPayment(): StripePaymentResultEvent? {
        val orderId = defaults.stringForKey(KEY_PENDING_ORDER_ID) ?: return null
        nextEventId = 2L
        return StripePaymentResultEvent(
            eventId = 1L,
            orderId = orderId,
            result = StripePaymentResult.RECOVERY_REQUIRED,
        )
    }

    private companion object {
        const val KEY_PENDING_ORDER_ID = "qmarket.ios.stripe.pendingOrderId"
    }
}
