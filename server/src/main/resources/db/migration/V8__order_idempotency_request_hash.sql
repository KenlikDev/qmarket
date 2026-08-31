-- Fingerprint of CreateOrderRequest for Idempotency-Key conflict detection.
-- Empty string = legacy rows (accept any body on replay).
ALTER TABLE order_idempotency_keys
    ADD COLUMN IF NOT EXISTS request_hash VARCHAR(64) NOT NULL DEFAULT '';
