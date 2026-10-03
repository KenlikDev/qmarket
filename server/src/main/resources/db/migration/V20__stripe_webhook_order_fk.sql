-- Restore referential integrity for durable Stripe webhook audit rows.
-- Existing webhook history is checked explicitly so a legacy orphan cannot silently
-- bypass the new constraint during rollout.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM stripe_webhook_events swe
        LEFT JOIN orders o ON o.id = swe.order_id
        WHERE swe.order_id IS NOT NULL
          AND o.id IS NULL
    ) THEN
        RAISE EXCEPTION
            'Cannot add webhook order foreign key: stripe_webhook_events contains orphan order_id values';
    END IF;
END $$;

ALTER TABLE stripe_webhook_events
    ADD CONSTRAINT fk_stripe_webhook_events_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;
