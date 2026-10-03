-- Retention support for durable idempotency, webhook, and refresh-token rows.
-- Existing timestamp indexes are used by bounded cleanup queries.
COMMENT ON TABLE order_idempotency_keys IS
    'Checkout idempotency mappings retained for the configured replay window.';
COMMENT ON TABLE stripe_webhook_events IS
    'Stripe webhook deduplication/audit rows retained for the configured replay/reconciliation window.';
COMMENT ON TABLE refresh_tokens IS
    'Refresh-token history retained through expiry plus the configured safety window.';
