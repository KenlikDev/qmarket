-- Persist the effective payment currency for each order/payment flow.
-- Existing QMarket orders were single-currency (rub) before payment currency became explicit.
ALTER TABLE orders
    ADD COLUMN payment_currency VARCHAR(3);

UPDATE orders
SET payment_currency = 'rub'
WHERE payment_currency IS NULL;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_payment_currency
    CHECK (payment_currency IS NULL OR payment_currency ~ '^[a-z]{3}$');
