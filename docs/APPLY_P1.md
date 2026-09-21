> **Historical note:** P1 was already applied to the repository. This document records the original manual application procedure and is retained for audit/history. Do not reapply these steps to the current tree.

# P1: typed Stripe webhook JSON + amount reconciliation

## Apply after P0 (`qmarket-stripe-webhook-p0`)

### New / replaced files

| Path | Action |
|------|--------|
| `server/order/.../payment/StripeWebhookPayloads.kt` | **add** |
| `server/order/.../payment/StripeWebhookService.kt` | **replace** (needs `ObjectMapper` ctor arg) |
| `server/order/.../payment/StripeWebhookServiceTest.kt` | **replace** |
| `OrderService.markPaidFromProvider` | **patch signature** — see method below |

### `OrderService.markPaidFromProvider` (replace method only)

Add optional params and amount check:

```kotlin
@Transactional
fun markPaidFromProvider(
    orderId: UUID,
    providerId: String,
    providerReference: String?,
    amountMinor: Long? = null,
    currency: String? = null,
): OrderResponse {
    // ... existing status checks ...

    if (amountMinor != null) {
        val expectedMinor =
            order.totalAmount
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        if (amountMinor != expectedMinor) {
            throw BadRequestException(
                "Payment amount mismatch for order $orderId: " +
                    "provider=$amountMinor minor, order=$expectedMinor minor",
            )
        }
    }
    if (!currency.isNullOrBlank() && currency.lowercase().length != 3) {
        throw BadRequestException("Invalid provider currency: $currency")
    }

    // ... existing markPaid + notify ...
}
```

Default params keep existing call sites compiling.

### Tests

```bash
./gradlew :server:order:test --tests '*StripeWebhook*' ktlintCheck --parallel
```

If `jackson-module-kotlin` is missing in test classpath, Spring Boot’s `ObjectMapper` bean is used in production; tests use `jacksonObjectMapper()` — add `testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")` only if compile fails.
