-- Optimistic locking for concurrent profile/password/OAuth updates.
ALTER TABLE users
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
