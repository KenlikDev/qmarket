package com.kenlikdev.qmarket

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kenlikdev.qmarket.api.PaymentSessionDto
import com.kenlikdev.qmarket.network.StripePaymentResult
import com.kenlikdev.qmarket.network.initAndroidSessionStore
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

class MainActivity : ComponentActivity() {
    private lateinit var paymentSheet: PaymentSheet
    private var pendingStripeResult: ((StripePaymentResult) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        initAndroidSessionStore(this)

        // Register the Activity Result launcher before the Activity reaches STARTED.
        // The publishable key is provided by the server with each payment session and is
        // installed immediately before presentation; no key is hard-coded in the client.
        paymentSheet = PaymentSheet.Builder(::onPaymentSheetResult).build(this)

        setContent {
            App(
                apiBaseUrl = getString(R.string.qmarket_api_base_url),
                onConfirmStripePayment = { session, onResult ->
                    presentStripePaymentSheet(session, onResult)
                },
            )
        }
    }

    private fun presentStripePaymentSheet(
        session: PaymentSessionDto,
        onResult: (StripePaymentResult) -> Unit,
    ) {
        val publishableKey = session.publishableKey
        if (
            session.providerId != "stripe" ||
            session.clientSecret.isBlank() ||
            !publishableKey.matches(Regex("^pk_(test|live)_[A-Za-z0-9]+$"))
        ) {
            onResult(StripePaymentResult.FAILED)
            return
        }

        try {
            PaymentConfiguration.init(applicationContext, publishableKey)
            pendingStripeResult = onResult
            paymentSheet.presentWithPaymentIntent(
                session.clientSecret,
                PaymentSheet.Configuration.Builder("QMarket").build(),
            )
        } catch (_: Exception) {
            pendingStripeResult = null
            onResult(StripePaymentResult.FAILED)
        }
    }

    private fun onPaymentSheetResult(result: PaymentSheetResult) {
        val callback = pendingStripeResult
        pendingStripeResult = null
        callback?.invoke(
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
