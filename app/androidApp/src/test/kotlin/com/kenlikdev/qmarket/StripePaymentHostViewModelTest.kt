package com.kenlikdev.qmarket

import androidx.lifecycle.SavedStateHandle
import com.kenlikdev.qmarket.network.StripePaymentResult
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StripePaymentHostViewModelTest {
    @Test
    fun resultEventCanBeRestoredAfterHostViewModelRecreation() {
        val savedState = SavedStateHandle()
        val firstHost = StripePaymentHostViewModel(savedState)
        firstHost.begin("order-42")
        firstHost.publishResult("order-42", StripePaymentResult.COMPLETED)

        val originalEvent = assertNotNull(firstHost.paymentResult.value)
        assertEquals("order-42", firstHost.pendingOrderId)

        // The system restores SavedStateHandle, while the ViewModel itself is new.
        val restoredState =
            SavedStateHandle(
                mapOf(
                    "qmarket.stripe.pendingOrderId" to savedState.get<String>("qmarket.stripe.pendingOrderId"),
                    "qmarket.stripe.nextResultEventId" to savedState.get<Long>("qmarket.stripe.nextResultEventId"),
                    "qmarket.stripe.resultEventId" to savedState.get<Long>("qmarket.stripe.resultEventId"),
                    "qmarket.stripe.resultOrderId" to savedState.get<String>("qmarket.stripe.resultOrderId"),
                    "qmarket.stripe.resultValue" to savedState.get<String>("qmarket.stripe.resultValue"),
                ),
            )
        val recreatedHost = StripePaymentHostViewModel(restoredState)
        assertEquals(originalEvent, recreatedHost.paymentResult.value)
        assertEquals("order-42", recreatedHost.pendingOrderId)

        recreatedHost.consumeResult(originalEvent.eventId)

        assertNull(recreatedHost.paymentResult.value)
        assertNull(recreatedHost.pendingOrderId)
    }

    @Test
    fun duplicateBeginForSameOrderCannotLaunchAnotherPaymentSheet() {
        val host = StripePaymentHostViewModel(SavedStateHandle())

        assertTrue(host.begin("order-1"))
        assertFalse(host.begin("order-1"))
        assertFalse(host.begin("order-2"))

        assertEquals("order-1", host.pendingOrderId)
    }

    @Test
    fun mismatchedEventIdCannotConsumePendingResult() {
        val host = StripePaymentHostViewModel(SavedStateHandle())
        host.begin("order-1")
        host.publishResult("order-1", StripePaymentResult.CANCELED)
        val firstEvent = assertNotNull(host.paymentResult.value)

        host.consumeResult(firstEvent.eventId + 1)

        assertEquals(firstEvent, host.paymentResult.value)
        assertEquals("order-1", host.pendingOrderId)
    }

    @Test
    fun callbackForDifferentOrderIsIgnored() {
        val host = StripePaymentHostViewModel(SavedStateHandle())
        host.begin("current-order")
        host.publishResult("stale-order", StripePaymentResult.FAILED)

        assertNull(host.paymentResult.value)
        assertEquals("current-order", host.pendingOrderId)
    }
}
