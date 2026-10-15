package com.kenlikdev.qmarket

import com.kenlikdev.qmarket.network.StripePaymentResult
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import platform.Foundation.NSUserDefaults

class IosStripePaymentHostTest {
    private val defaults = NSUserDefaults.standardUserDefaults

    @BeforeTest
    fun clearPendingPaymentBeforeTest() {
        defaults.removeObjectForKey(PENDING_ORDER_ID_KEY)
    }

    @AfterTest
    fun clearPendingPaymentAfterTest() {
        defaults.removeObjectForKey(PENDING_ORDER_ID_KEY)
    }

    @Test
    fun onlyOnePaymentCanBePendingAndAStaleOrderCallbackIsIgnored() {
        val host = IosStripePaymentHost()

        assertTrue(host.beginPayment("order-1"))
        assertFalse(host.beginPayment("order-2"))

        host.publishPaymentResult(orderId = "order-2", outcome = "completed")
        assertNull(host.stripePaymentResult, "A callback for another order must be ignored.")

        host.publishPaymentResult(orderId = "order-1", outcome = "canceled")
        val event = assertNotNull(host.stripePaymentResult)
        assertEquals("order-1", event.orderId)
        assertEquals(StripePaymentResult.CANCELED, event.result)

        host.consumePaymentResult(event.eventId)

        assertNull(host.stripePaymentResult)
        assertNull(defaults.stringForKey(PENDING_ORDER_ID_KEY))
    }

    @Test
    fun secondCallbackCannotOverwriteAnUnconsumedResult() {
        val host = IosStripePaymentHost()
        assertTrue(host.beginPayment("order-3"))

        host.publishPaymentResult(orderId = "order-3", outcome = "completed")
        val firstEvent = assertNotNull(host.stripePaymentResult)

        host.publishPaymentResult(orderId = "order-3", outcome = "failed")

        assertEquals(firstEvent, host.stripePaymentResult)
        host.consumePaymentResult(firstEvent.eventId)
        assertNull(defaults.stringForKey(PENDING_ORDER_ID_KEY))
    }

    @Test
    fun recreatedHostReconcilesThePendingOrderWithoutPersistingPaymentSecrets() {
        val originalHost = IosStripePaymentHost()
        assertTrue(originalHost.beginPayment("order-after-restart"))

        // Constructing a new host mirrors an app-process restart. It emits a reconciliation
        // event for the persisted order identifier; it does not persist an SDK result or secret.
        val restoredHost = IosStripePaymentHost()
        val event = assertNotNull(restoredHost.stripePaymentResult)

        assertEquals("order-after-restart", event.orderId)
        assertEquals(StripePaymentResult.COMPLETED, event.result)
        assertEquals("order-after-restart", defaults.stringForKey(PENDING_ORDER_ID_KEY))

        restoredHost.consumePaymentResult(event.eventId)

        assertNull(defaults.stringForKey(PENDING_ORDER_ID_KEY))
        assertNull(restoredHost.stripePaymentResult)
    }

    private companion object {
        const val PENDING_ORDER_ID_KEY = "qmarket.ios.stripe.pendingOrderId"
    }
}
