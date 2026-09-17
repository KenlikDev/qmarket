-- Persistent idempotency for Stripe webhook events (replaces in-memory ConcurrentHashMap).
-- Claim = INSERT; duplicate event_id is ignored. Business processing runs only after claim.
CREATE TABLE stripe_webhook_events (
    event_id            VARCHAR(64)  PRIMARY KEY,
    event_type          VARCHAR(128) NOT NULL,
    status              VARCHAR(32)  NOT NULL,
    provider_reference  VARCHAR(128),
    order_id            UUID,
    received_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMPTZ,
    error_message       VARCHAR(512)
);

CREATE INDEX idx_stripe_webhook_events_status ON stripe_webhook_events(status);
CREATE INDEX idx_stripe_webhook_events_received ON stripe_webhook_events(received_at);
