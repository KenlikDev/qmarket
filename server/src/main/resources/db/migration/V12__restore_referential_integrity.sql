-- Restore referential integrity for aggregates that already exist in the schema.
-- Historical migrations are intentionally left unchanged.

ALTER TABLE carts
    ADD CONSTRAINT fk_carts_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_items_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT;

ALTER TABLE order_items
    ADD CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT;

ALTER TABLE refresh_tokens
    ADD CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_notifications
    ADD CONSTRAINT fk_user_notifications_user
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;

ALTER TABLE user_notifications
    ADD CONSTRAINT fk_user_notifications_order
        FOREIGN KEY (related_order_id) REFERENCES orders(id) ON DELETE SET NULL;

CREATE INDEX idx_cart_items_product ON cart_items(product_id);
CREATE INDEX idx_order_items_product ON order_items(product_id);
CREATE INDEX idx_user_notifications_related_order ON user_notifications(related_order_id);
