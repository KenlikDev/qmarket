-- Persist a payment/reservation deadline for every order.
-- Historical orders retain the same 15-minute window used by the application default.
ALTER TABLE orders
    ADD COLUMN payment_expires_at TIMESTAMPTZ;

UPDATE orders
SET payment_expires_at = created_at + INTERVAL '15 minutes'
WHERE payment_expires_at IS NULL;

ALTER TABLE orders
    ALTER COLUMN payment_expires_at SET NOT NULL;

CREATE INDEX idx_orders_payment_expiry_due
    ON orders (payment_expires_at, id)
    WHERE status IN ('PENDING', 'CONFIRMED');
