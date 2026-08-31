-- Optimistic locking for order status races and product metadata vs stock races.
ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
