# Durable data retention

QMarket keeps three classes of durable operational data and removes old rows with a scheduled, bounded cleanup job.

| Data | Default retention | Cleanup timestamp | Safety rule |
|---|---:|---|---|
| Order idempotency keys | 30 days | `created_at` | The replay guarantee lasts through the configured retention window. |
| Stripe webhook events | 30 days | `received_at` | Keep the deduplication/audit record through the configured reconciliation window. |
| Refresh tokens | 7 days after expiry | `expires_at` | Never delete an unexpired token; expired rows get an additional safety window. |

Configuration is under `qmarket.data-retention`:

- `enabled`
- `cleanup-interval-ms`
- `initial-delay-ms`
- `batch-size`
- `idempotency-retention-days`
- `webhook-retention-days`
- `refresh-token-retention-after-expiry-days`

Cleanup deletes at most one bounded batch from each table per scheduled invocation. Existing timestamp indexes on the three tables support the ordered batch selection.

The cleanup job performs only local database work. It does not call payment providers or mutate protected application state.

## Idempotency contract

After an idempotency row is removed, reusing the same key is treated as a new checkout because the documented replay window has expired. Callers that require a longer replay guarantee must configure a longer retention period.

## Stripe webhook contract

Webhook event rows are retained independently from order state. Removing a row permits a very old duplicate event to be claimed again, so the business operation must remain idempotent. The current payment lifecycle is designed so a repeated provider-success event converges on the already-PAID order instead of restoring inventory twice.

## Refresh-token contract

Refresh tokens are never deleted while `expires_at` is in the future. After expiry, rows remain for the configured safety window so normal rotation/reuse checks do not race the cleanup job. Once the safety window has elapsed, the JWT is already expired and the historical row can be removed.
