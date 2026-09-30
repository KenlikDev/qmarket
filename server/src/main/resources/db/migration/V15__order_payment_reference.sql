-- Persist the provider identity for the single payment intent associated with an order.
ALTER TABLE orders
    ADD COLUMN payment_provider VARCHAR(32),
    ADD COLUMN payment_provider_reference VARCHAR(255);

CREATE UNIQUE INDEX uq_orders_payment_provider_reference
    ON orders (payment_provider, payment_provider_reference)
    WHERE payment_provider_reference IS NOT NULL;
