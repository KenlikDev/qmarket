package com.kenlikdev.qmarket.payment

import com.kenlikdev.qmarket.api.PaymentSessionDto
import kotlinx.browser.window
import kotlin.js.asDynamic

/**
 * Browser-only Stripe.js / Payment Element adapter.
 *
 * Stripe.js owns all card-entry fields; QMarket never receives card data. The returned browser
 * result is only a UI hint. The order becomes PAID only after the server reconciles Stripe's
 * signed webhook and the client refreshes the order resource.
 */
actual val supportsStripeClientConfirmation: Boolean = true

actual fun launchStripeClientConfirmation(
    session: PaymentSessionDto,
    onResult: (StripeConfirmationResult) -> Unit,
) {
    if (session.publishableKey.isBlank()) {
        onResult(
            StripeConfirmationResult(
                StripeConfirmationOutcome.UNAVAILABLE,
                "Stripe publishable key is missing. Payment session was not opened.",
            ),
        )
        return
    }
    if (session.clientSecret.isBlank()) {
        onResult(
            StripeConfirmationResult(
                StripeConfirmationOutcome.FAILED,
                "Stripe did not return a client secret. Refresh the order and retry.",
            ),
        )
        return
    }

    val browserWindow = window.asDynamic()
    val document = browserWindow.document

    fun openPaymentElement() {
        val stripeFactory = browserWindow.Stripe
        if (stripeFactory == null) {
            onResult(
                StripeConfirmationResult(
                    StripeConfirmationOutcome.UNAVAILABLE,
                    "Stripe.js could not be loaded. Check the network connection and retry.",
                ),
            )
            return
        }

        var overlay: dynamic = null
        var finished = false

        fun finish(result: StripeConfirmationResult) {
            if (finished) return
            finished = true
            if (overlay != null) {
                overlay.remove()
            }
            onResult(result)
        }

        try {
            overlay = document.createElement("div")
            overlay.id = "qmarketStripePaymentOverlay"
            overlay.setAttribute("role", "presentation")
            overlay.style.cssText =
                "position:fixed;inset:0;z-index:2147483000;" +
                "display:flex;align-items:center;justify-content:center;" +
                "padding:16px;background:rgba(10,15,25,.68);overflow:auto;"

            val panel = document.createElement("section")
            panel.setAttribute("role", "dialog")
            panel.setAttribute("aria-modal", "true")
            panel.setAttribute("aria-label", "Secure payment")
            panel.style.cssText =
                "box-sizing:border-box;width:min(100%,520px);max-height:calc(100vh - 32px);" +
                "overflow:auto;background:#fff;color:#172033;border-radius:16px;padding:24px;" +
                "box-shadow:0 24px 80px rgba(0,0,0,.28);text-align:left;" +
                "font:16px/1.45 system-ui,-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;"
            panel.innerHTML =
                """
                <div style="display:flex;align-items:center;justify-content:space-between;gap:12px">
                  <h2 style="font-size:21px;margin:0">Secure payment</h2>
                  <button id="qmarketStripeClose" type="button"
                    aria-label="Close payment"
                    style="border:0;background:transparent;font-size:26px;cursor:pointer">×</button>
                </div>
                <p style="margin:8px 0 18px;color:#526078">Order payment is handled securely by Stripe.</p>
                <div id="qmarketStripePaymentElement"></div>
                <p id="qmarketStripePaymentStatus" role="status" aria-live="polite"
                   style="min-height:24px;color:#a12622;margin:12px 0"></p>
                <div style="display:flex;justify-content:flex-end;gap:10px;flex-wrap:wrap">
                  <button id="qmarketStripeCancel" type="button"
                    style="padding:10px 16px;border:1px solid #c9d0da;border-radius:8px;background:#fff;cursor:pointer">
                    Cancel
                  </button>
                  <button id="qmarketStripeSubmit" type="button"
                    style="padding:10px 16px;border:0;border-radius:8px;background:#174ea6;color:#fff;cursor:pointer">
                    Pay securely
                  </button>
                </div>
                <p style="font-size:12px;color:#64748b;margin:16px 0 0">
                  QMarket updates the order after the server verifies the payment with Stripe.
                </p>
                """.trimIndent()

            overlay.appendChild(panel)
            document.body.appendChild(overlay)

            val statusNode = document.getElementById("qmarketStripePaymentStatus")
            val submitButton = document.getElementById("qmarketStripeSubmit")
            val cancelButton = document.getElementById("qmarketStripeCancel")
            val closeButton = document.getElementById("qmarketStripeClose")

            cancelButton.onclick = { event: dynamic ->
                event.preventDefault()
                finish(
                    StripeConfirmationResult(
                        StripeConfirmationOutcome.CANCELLED,
                        "Payment was cancelled. The order remains unpaid.",
                    ),
                )
                null
            }
            closeButton.onclick = { event: dynamic ->
                event.preventDefault()
                finish(
                    StripeConfirmationResult(
                        StripeConfirmationOutcome.CANCELLED,
                        "Payment was closed. The order remains unpaid.",
                    ),
                )
                null
            }

            val stripe = stripeFactory(session.publishableKey)
            val elementOptions = js("({})")
            elementOptions.clientSecret = session.clientSecret
            val elements = stripe.elements(elementOptions)
            val paymentElement = elements.create("payment")
            paymentElement.mount("#qmarketStripePaymentElement")

            var submitting = false
            submitButton.onclick = { event: dynamic ->
                event.preventDefault()
                if (!submitting && !finished) {
                    submitting = true
                    submitButton.disabled = true
                    statusNode.textContent = "Validating payment details…"

                    elements.submit()
                        .then({ submission: dynamic ->
                            if (submission.error != null) {
                                statusNode.textContent =
                                    submission.error.message ?: "Check the payment details and retry."
                                submitting = false
                                submitButton.disabled = false
                            } else {
                                statusNode.textContent = "Confirming payment with Stripe…"
                                val confirmParams = js("({})")
                                confirmParams.return_url = browserWindow.location.href
                                val confirmOptions = js("({})")
                                confirmOptions.elements = elements
                                confirmOptions.confirmParams = confirmParams
                                confirmOptions.redirect = "if_required"

                                stripe.confirmPayment(confirmOptions)
                                    .then({ confirmation: dynamic ->
                                        if (confirmation.error != null) {
                                            statusNode.textContent =
                                                confirmation.error.message ?: "Stripe could not confirm this payment."
                                            submitting = false
                                            submitButton.disabled = false
                                        } else {
                                            val intentStatus = confirmation.paymentIntent?.status as? String
                                            when (intentStatus) {
                                                "succeeded" ->
                                                    finish(
                                                        StripeConfirmationResult(
                                                            StripeConfirmationOutcome.SUCCEEDED,
                                                            "Stripe accepted the payment. Waiting for QMarket to verify it.",
                                                        ),
                                                    )
                                                "processing" ->
                                                    finish(
                                                        StripeConfirmationResult(
                                                            StripeConfirmationOutcome.PROCESSING,
                                                            "Payment is processing. QMarket will update the order after provider confirmation.",
                                                        ),
                                                    )
                                                else -> {
                                                    statusNode.textContent =
                                                        "Payment is not complete yet. Review the message and retry."
                                                    submitting = false
                                                    submitButton.disabled = false
                                                }
                                            }
                                        }
                                        null
                                    })
                                    .catch({ failure: dynamic ->
                                        statusNode.textContent =
                                            failure.message ?: "A network error occurred while confirming payment."
                                        submitting = false
                                        submitButton.disabled = false
                                        null
                                    })
                            }
                            null
                        })
                        .catch({ failure: dynamic ->
                            statusNode.textContent =
                                failure.message ?: "A network error occurred while validating payment details."
                            submitting = false
                            submitButton.disabled = false
                            null
                        })
                }
                null
            }
        } catch (failure: Throwable) {
            finish(
                StripeConfirmationResult(
                    StripeConfirmationOutcome.FAILED,
                    failure.message ?: "Could not open the Stripe payment form.",
                ),
            )
        }
    }

    // Load Stripe.js lazily rather than blocking startup for users who never open checkout.
    if (browserWindow.Stripe != null) {
        openPaymentElement()
    } else {
        val script = document.createElement("script")
        script.src = "https://js.stripe.com/v3/"
        script.async = true
        script.setAttribute("data-qmarket-stripe-js", "true")
        script.onload = {
            openPaymentElement()
            null
        }
        script.onerror = {
            onResult(
                StripeConfirmationResult(
                    StripeConfirmationOutcome.UNAVAILABLE,
                    "Stripe.js could not be loaded. Check the network connection and retry.",
                ),
            )
            null
        }
        document.head.appendChild(script)
    }
}
