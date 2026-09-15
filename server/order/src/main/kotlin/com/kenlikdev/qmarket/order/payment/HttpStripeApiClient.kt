package com.kenlikdev.qmarket.order.payment

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

/**
 * Stripe PaymentIntents via JDK [HttpClient] (no official SDK dependency).
 */
@Component
@ConditionalOnProperty(name = ["qmarket.payment.provider"], havingValue = "stripe")
class HttpStripeApiClient(
    private val props: StripeProperties,
    private val httpClient: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build(),
) : StripeApiClient {
    override fun createPaymentIntent(
        amountMinor: Long,
        currency: String,
        orderId: UUID,
        userId: UUID,
    ): StripePaymentIntentResult {
        require(props.secretKey.isNotBlank()) {
            "qmarket.payment.stripe.secret-key is required when provider=stripe"
        }
        val form =
            mapOf(
                "amount" to amountMinor.toString(),
                "currency" to currency.lowercase(),
                "confirm" to "true",
                // Test-mode payment method; replace with client PM / Elements in production.
                "payment_method" to "pm_card_visa",
                "metadata[order_id]" to orderId.toString(),
                "metadata[user_id]" to userId.toString(),
            ).entries.joinToString("&") { (k, v) ->
                "${enc(k)}=${enc(v)}"
            }

        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("${props.apiBaseUrl.trimEnd('/')}/v1/payment_intents"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer ${props.secretKey}")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val body = response.body()
        if (response.statusCode() !in 200..299) {
            val message = extractJsonString(body, "message") ?: "Stripe HTTP ${response.statusCode()}"
            throw StripeApiException(message, response.statusCode(), body)
        }
        val id = extractJsonString(body, "id") ?: error("Stripe response missing id: $body")
        val status = extractJsonString(body, "status") ?: "unknown"
        return StripePaymentIntentResult(id = id, status = status, rawBody = body)
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun extractJsonString(
        json: String,
        field: String,
    ): String? {
        val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.find(json)?.groupValues?.get(1)
    }
}

class StripeApiException(
    message: String,
    val statusCode: Int,
    val responseBody: String?,
) : RuntimeException(message)
