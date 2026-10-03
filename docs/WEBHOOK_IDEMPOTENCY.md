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
- 0 + status `RECEIVED` → the row is locked with `FOR UPDATE`; a concurrent worker waits for the current transaction and then observes the final state.

Business work runs **after** claim; status is updated to `PROCESSED` only on success, or `FAILED` with error message on exception (Stripe can retry).

## Current implementation

- Webhook payloads are parsed into typed Jackson/Kotlin DTOs with required-field validation.
- PaymentIntent amount is reconciled against the order total before marking it paid.
- Currency is validated against the order's persisted payment currency, so configuration changes cannot silently change the expected currency for an existing order.
- Event status and failure state are persisted in PostgreSQL so redelivery can retry failed processing.


## Payment-operation lifecycle

Order payment operations are durable. Payment-session creation and cancellation persist an operation state before provider I/O, then finalize it in a short transaction. A provider failure leaves the operation recoverable; stale operations can be reclaimed by the expiry/recovery scheduler. Terminal orders must not retain an in-flight payment operation.

## Retention

Webhook deduplication rows are retained for a configurable default of 30 days using `received_at`. The retention job deletes old rows in bounded batches so routine cleanup does not require a long-running table operation.

The retention window must be long enough for the operational webhook redelivery/reconciliation guarantee used by the deployment. After a row leaves the retention window, an extremely old duplicate event may be claimed again; webhook business handling therefore remains idempotent for terminal payment states.

See `docs/DATA_RETENTION.md` for the full retention policy and configuration keys.


