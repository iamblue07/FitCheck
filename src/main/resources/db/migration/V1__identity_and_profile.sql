CREATE TABLE users (
    id            uuid PRIMARY KEY,
    email         text NOT NULL UNIQUE,
    password_hash text NOT NULL,
    created_at    timestamp NOT NULL,
    updated_at    timestamp
);

CREATE TABLE user_profiles (
    user_id                   uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    birth_date                date,
    sex                       text,
    height_cm                 numeric(5, 2),
    weight_kg                 numeric(5, 2),
    foot_length_cm            numeric(4, 1),
    average_budget_per_outfit numeric(10, 2),
    currency                  text,
    updated_at                timestamp
);

CREATE TABLE user_body_photos (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    photo_type text NOT NULL,
    file_path  text NOT NULL,
    created_at timestamp NOT NULL,
    updated_at timestamp,
    CONSTRAINT uq_user_body_photos_user_type UNIQUE (user_id, photo_type)
);

CREATE TABLE style_tags (
    id   uuid PRIMARY KEY,
    name text NOT NULL UNIQUE
);

CREATE TABLE user_style_preferences (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    style_tag_id uuid NOT NULL REFERENCES style_tags (id),
    CONSTRAINT uq_user_style_preferences UNIQUE (user_id, style_tag_id)
);