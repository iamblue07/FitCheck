ALTER TABLE users
    ADD COLUMN role text NOT NULL DEFAULT 'user';

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamp NOT NULL,
    revoked_at timestamp,
    created_at timestamp NOT NULL,
    updated_at timestamp
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);