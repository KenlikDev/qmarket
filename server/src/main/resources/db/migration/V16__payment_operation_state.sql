-- Track in-flight payment operations so provider calls can run outside database transactions.
-- The operation key is durable and lets recovery/cancellation resolve an intent that was created
-- before its provider reference was persisted locally.
ALTER TABLE orders
    ADD COLUMN payment_operation_state VARCHAR(32) NOT NULL DEFAULT 'NONE',
    ADD COLUMN payment_operation_key VARCHAR(255);

CREATE UNIQUE INDEX uq_orders_payment_operation_key
    ON orders (payment_operation_key)
    WHERE payment_operation_key IS NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_payment_operation_state
    CHECK (payment_operation_state IN ('NONE', 'CREATING', 'CANCELLING'));
