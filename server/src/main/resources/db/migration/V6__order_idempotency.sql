-- Idempotency keys for POST /api/v1/orders (checkout).
-- Same (user_id, key) always maps to the same order_id within the retention window.
CREATE TABLE order_idempotency_keys (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL,
    idem_key     VARCHAR(128) NOT NULL,
    order_id     UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_order_idempotency_user_key UNIQUE (user_id, idem_key)
);

CREATE INDEX idx_order_idempotency_order ON order_idempotency_keys(order_id);
CREATE INDEX idx_order_idempotency_created ON order_idempotency_keys(created_at);
