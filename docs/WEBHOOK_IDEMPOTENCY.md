# Stripe webhook idempotency (P0)

## Problem (before)

`StripeWebhookService` used `ConcurrentHashMap.newKeySet<String>()` for processed event ids.

- Lost on restart / multi-instance
- Event could be marked “seen” before `markPaidFromProvider` succeeded → permanent skip on Stripe retry

## Solution

Table `stripe_webhook_events` (Flyway `V11__stripe_webhook_events.sql`):

| Column | Role |
|--------|------|
| `event_id` PK | Stripe event id |
| `status` | `RECEIVED` → `PROCESSED` \| `FAILED` |
| `provider_reference` | PaymentIntent id |
| `order_id` | Linked order when applicable |

Claim:

```sql
INSERT INTO stripe_webhook_events (event_id, event_type, status, received_at)
VALUES (?, ?, 'RECEIVED', NOW())
ON CONFLICT (event_id) DO NOTHING
```

- rows affected = 1 → this worker owns processing
- 0 + status `PROCESSED` → no-op
- 0 + status `FAILED` → allow retry (Stripe redelivery after transient error)
- 0 + status `RECEIVED` → another worker in-flight; skip

Business work runs **after** claim; status is updated to `PROCESSED` only on success, or `FAILED` with error message on exception (Stripe can retry).

## Current implementation

- Webhook payloads are parsed with Jackson `JsonNode` validation.
- PaymentIntent amount is reconciled against the order total before marking it paid.
- Currency is validated against the configured Stripe currency.
- Event status and failure state are persisted in PostgreSQL so redelivery can retry failed processing.
