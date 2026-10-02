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

-- Recover operation metadata for in-flight states created by pre-V18 application versions.
UPDATE orders
SET payment_operation_id = COALESCE(payment_operation_id, gen_random_uuid()),
    payment_operation_started_at = COALESCE(payment_operation_started_at, updated_at)
WHERE payment_operation_state <> 'NONE';

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_payment_operation_metadata
    CHECK (
        (payment_operation_state = 'NONE'
            AND payment_operation_id IS NULL
            AND payment_operation_started_at IS NULL)
        OR
        (payment_operation_state <> 'NONE'
            AND payment_operation_id IS NOT NULL
            AND payment_operation_started_at IS NOT NULL)
    );

CREATE INDEX idx_orders_payment_expiry_due
    ON orders (payment_expires_at, id)
    WHERE status IN ('PENDING', 'CONFIRMED');
