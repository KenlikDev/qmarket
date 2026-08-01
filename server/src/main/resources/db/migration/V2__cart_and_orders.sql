CREATE TABLE carts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_carts_user ON carts(user_id);

CREATE TABLE cart_items (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cart_id     UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id  UUID NOT NULL,
    quantity    INT NOT NULL CHECK (quantity > 0),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart ON cart_items(cart_id);

CREATE TABLE orders (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL,
    status            VARCHAR(32) NOT NULL,
    total_amount      NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    shipping_address  VARCHAR(500),
    customer_note     VARCHAR(1000),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created ON orders(created_at DESC);

CREATE TABLE order_items (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id      UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    product_id    UUID NOT NULL,
    product_name  VARCHAR(255) NOT NULL,
    product_slug  VARCHAR(255) NOT NULL,
    unit_price    NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    quantity      INT NOT NULL CHECK (quantity > 0),
    line_total    NUMERIC(12, 2) NOT NULL CHECK (line_total >= 0)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);
