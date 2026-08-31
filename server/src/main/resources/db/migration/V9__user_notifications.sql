-- In-app notifications (email/push channels later).
CREATE TABLE user_notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    type            VARCHAR(64) NOT NULL,
    title           VARCHAR(200) NOT NULL,
    body            VARCHAR(1000) NOT NULL,
    related_order_id UUID,
    is_read         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_notifications_user_created
    ON user_notifications (user_id, created_at DESC);

CREATE INDEX idx_user_notifications_user_unread
    ON user_notifications (user_id)
    WHERE is_read = FALSE;
