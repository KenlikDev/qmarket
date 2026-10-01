-- Persist a payment/reservation deadline and operation start time.
-- The application uses a 15-minute default payment window.
ALTER TABLE orders
    ADD COLUMN payment_expires_at TIMESTAMPTZ;

UPDATE orders
SET payment_expires_at = created_at + INTERVAL '15 minutes'
WHERE payment_expires_at IS NULL;

ALTER TABLE orders
    ALTER COLUMN payment_expires_at SET NOT NULL;

ALTER TABLE orders
    ADD COLUMN payment_operation_id UUID,
    ADD COLUMN payment_operation_started_at TIMESTAMPTZ;

CREATE INDEX idx_orders_payment_expiry_due
    ON orders (payment_expires_at, id)
    WHERE status IN ('PENDING', 'CONFIRMED');
