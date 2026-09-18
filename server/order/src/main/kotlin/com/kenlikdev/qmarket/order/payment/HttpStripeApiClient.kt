package com.kenlikdev.qmarket.order.payment

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
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
    private val objectMapper: ObjectMapper,
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
        val form =
            baseForm(amountMinor, currency, orderId, userId) +
                mapOf(
                    "confirm" to "true",
                    // Legacy server-side charge path; disabled at the gateway layer.
                    "payment_method" to "pm_card_visa",
                )
        return postPaymentIntent(form)
    }

    override fun createPaymentIntentForClient(
        amountMinor: Long,
        currency: String,
        orderId: UUID,
        userId: UUID,
    ): StripePaymentIntentResult {
        val form =
            baseForm(amountMinor, currency, orderId, userId) +
                mapOf(
                    "automatic_payment_methods[enabled]" to "true",
                )
        return postPaymentIntent(form)
    }

    private fun baseForm(
        amountMinor: Long,
        currency: String,
        orderId: UUID,
        userId: UUID,
    ): Map<String, String> =
        mapOf(
            "amount" to amountMinor.toString(),
            "currency" to currency.lowercase(),
            "metadata[order_id]" to orderId.toString(),
            "metadata[user_id]" to userId.toString(),
        )

    private fun postPaymentIntent(fields: Map<String, String>): StripePaymentIntentResult {
        require(props.secretKey.isNotBlank()) {
            "qmarket.payment.stripe.secret-key is required when provider=stripe"
        }

        val form =
            fields.entries.joinToString("&") { (key, value) ->
                "${enc(key)}=${enc(value)}"
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
        val root =
            try {
                objectMapper.readTree(body)
            } catch (_: Exception) {
                throw StripeApiException(
                    message = "Stripe returned invalid JSON",
                    statusCode = response.statusCode(),
                    responseBody = body,
                )
            }

        if (response.statusCode() !in 200..299) {
            val message =
                root
                    .path("error")
                    .path("message")
                    .asString(null)
                    ?: "Stripe HTTP ${response.statusCode()}"
            throw StripeApiException(message, response.statusCode(), body)
        }

        val id =
            root.path("id").asString(null)
                ?: throw StripeApiException(
                    message = "Stripe response missing id",
                    statusCode = response.statusCode(),
                    responseBody = body,
                )
        val status = root.path("status").asString("unknown") ?: "unknown"
        val clientSecret = root.path("client_secret").asString(null)

        return StripePaymentIntentResult(
            id = id,
            status = status,
            clientSecret = clientSecret,
            rawBody = body,
        )
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

class StripeApiException(
    message: String,
    val statusCode: Int,
    val responseBody: String?,
) : RuntimeException(message)
