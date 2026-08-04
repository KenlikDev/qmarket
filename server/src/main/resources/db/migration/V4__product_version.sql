-- Optimistic locking column for concurrent product updates / stock
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
