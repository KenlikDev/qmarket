package com.kenlikdev.qmarket

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.kenlikdev.qmarket.network.StripePaymentResult
import com.kenlikdev.qmarket.network.StripePaymentResultEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Retains the non-secret routing state required by Stripe's Activity Result callback.
 *
 * Only an order id and result enum are written to SavedStateHandle. The PaymentIntent client
 * secret is never persisted here. Retaining the event allows a recreated Activity/Compose tree
 * to reconcile the order even when the provider callback arrives during recreation.
 */
class StripePaymentHostViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _paymentResult = MutableStateFlow(loadSavedResult())
    val paymentResult: StateFlow<StripePaymentResultEvent?> = _paymentResult.asStateFlow()

    val pendingOrderId: String?
        get() = savedStateHandle[KEY_PENDING_ORDER_ID]

    fun begin(orderId: String) {
        savedStateHandle[KEY_PENDING_ORDER_ID] = orderId
    }

    fun publishResult(
        orderId: String,
        result: StripePaymentResult,
    ) {
        val pendingId = pendingOrderId
        if (pendingId != null && pendingId != orderId) {
            // Ignore an obsolete callback if another order now owns the native payment sheet.
            return
        }
        if (_paymentResult.value != null) {
            // A previous result has not yet been handed to the shared app; don't overwrite it.
            return
        }

        val eventId = (savedStateHandle.get<Long>(KEY_NEXT_EVENT_ID) ?: 0L) + 1L
        val event = StripePaymentResultEvent(eventId = eventId, orderId = orderId, result = result)

        // Persist the event before exposing it to Compose so process recreation cannot lose it.
        savedStateHandle[KEY_NEXT_EVENT_ID] = eventId
        savedStateHandle[KEY_RESULT_EVENT_ID] = event.eventId
        savedStateHandle[KEY_RESULT_ORDER_ID] = event.orderId
        savedStateHandle[KEY_RESULT_VALUE] = event.result.name
        _paymentResult.value = event
    }

    fun consumeResult(eventId: Long) {
        val current = _paymentResult.value ?: return
        if (current.eventId != eventId) return

        _paymentResult.value = null
        savedStateHandle.remove<Long>(KEY_RESULT_EVENT_ID)
        savedStateHandle.remove<String>(KEY_RESULT_ORDER_ID)
        savedStateHandle.remove<String>(KEY_RESULT_VALUE)
        if (pendingOrderId == current.orderId) {
            savedStateHandle.remove<String>(KEY_PENDING_ORDER_ID)
        }
    }

    private fun loadSavedResult(): StripePaymentResultEvent? {
        val eventId = savedStateHandle.get<Long>(KEY_RESULT_EVENT_ID) ?: return null
        val orderId = savedStateHandle.get<String>(KEY_RESULT_ORDER_ID) ?: return null
        val resultName = savedStateHandle.get<String>(KEY_RESULT_VALUE) ?: return null
        val result = runCatching { StripePaymentResult.valueOf(resultName) }.getOrNull() ?: return null
        return StripePaymentResultEvent(eventId = eventId, orderId = orderId, result = result)
    }

    private companion object {
        const val KEY_PENDING_ORDER_ID = "qmarket.stripe.pendingOrderId"
        const val KEY_NEXT_EVENT_ID = "qmarket.stripe.nextResultEventId"
        const val KEY_RESULT_EVENT_ID = "qmarket.stripe.resultEventId"
        const val KEY_RESULT_ORDER_ID = "qmarket.stripe.resultOrderId"
        const val KEY_RESULT_VALUE = "qmarket.stripe.resultValue"
    }
}
