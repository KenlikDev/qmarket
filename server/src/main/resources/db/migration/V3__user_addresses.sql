CREATE TABLE user_addresses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label           VARCHAR(100),
    recipient_name  VARCHAR(200) NOT NULL,
    phone           VARCHAR(30),
    country         VARCHAR(100) NOT NULL DEFAULT 'RU',
    region          VARCHAR(100),
    city            VARCHAR(100) NOT NULL,
    street_line1    VARCHAR(255) NOT NULL,
    street_line2    VARCHAR(255),
    postal_code     VARCHAR(20),
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_addresses_user ON user_addresses(user_id);
CREATE UNIQUE INDEX uq_user_addresses_one_default
    ON user_addresses(user_id)
    WHERE is_default = TRUE;
