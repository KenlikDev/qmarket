package com.kenlikdev.qmarket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModelProvider
import com.kenlikdev.qmarket.api.PaymentSessionDto
import com.kenlikdev.qmarket.network.StripePaymentResult
import com.kenlikdev.qmarket.network.initAndroidSessionStore
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

class MainActivity : ComponentActivity() {
    private lateinit var paymentSheet: PaymentSheet
    private lateinit var paymentHost: StripePaymentHostViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidSessionStore(this)
        paymentHost = ViewModelProvider(this)[StripePaymentHostViewModel::class.java]

        // Register the Activity Result launcher before the Activity reaches STARTED.
        // The ViewModel retains only non-secret routing state so a recreated Activity can still
        // route the PaymentSheet callback to the current shared app model.
        paymentSheet = PaymentSheet.Builder(::onPaymentSheetResult).build(this)

        setContent {
            val paymentResult by paymentHost.paymentResult.collectAsState()
            App(
                apiBaseUrl = getString(R.string.qmarket_api_base_url),
                onConfirmStripePayment = ::presentStripePaymentSheet,
                stripePaymentResult = paymentResult,
                onStripePaymentResultConsumed = { eventId -> paymentHost.consumeResult(eventId) },
            )
        }
    }

    private fun presentStripePaymentSheet(session: PaymentSessionDto) {
        // Save only the order id before validating/presenting. It lets a failure result use the
        // same path as a PaymentSheet callback and never stores the client secret.
        paymentHost.begin(session.orderId)
        val publishableKey = session.publishableKey
        if (
            session.providerId != "stripe" ||
            session.clientSecret.isBlank() ||
            !publishableKey.matches(Regex("^pk_(test|live)_[A-Za-z0-9]+$"))
        ) {
            paymentHost.publishResult(session.orderId, StripePaymentResult.FAILED)
            return
        }

        try {
            PaymentConfiguration.init(applicationContext, publishableKey)
            paymentSheet.presentWithPaymentIntent(
                session.clientSecret,
                PaymentSheet.Configuration.Builder("QMarket").build(),
            )
        } catch (_: Exception) {
            paymentHost.publishResult(session.orderId, StripePaymentResult.FAILED)
        }
    }

    private fun onPaymentSheetResult(result: PaymentSheetResult) {
        val orderId = paymentHost.pendingOrderId ?: return
        paymentHost.publishResult(
            orderId,
            when (result) {
                is PaymentSheetResult.Completed -> StripePaymentResult.COMPLETED
                is PaymentSheetResult.Canceled -> StripePaymentResult.CANCELED
                is PaymentSheetResult.Failed -> StripePaymentResult.FAILED
            },
        )
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App(apiBaseUrl = "http://10.0.2.2:8080")
}
