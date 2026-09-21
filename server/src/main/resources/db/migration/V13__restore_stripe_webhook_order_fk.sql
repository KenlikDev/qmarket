-- Keep webhook history independent from an order lifecycle while preserving referential integrity.
ALTER TABLE stripe_webhook_events
    ADD CONSTRAINT fk_stripe_webhook_events_order
        FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL;

CREATE INDEX idx_stripe_webhook_events_order
    ON stripe_webhook_events(order_id);
