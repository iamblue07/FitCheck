CREATE TABLE outfits (
    id                  uuid PRIMARY KEY,
    source              text NOT NULL,
    compatibility_score numeric(6, 4),
    created_at          timestamp NOT NULL,
    updated_at          timestamp
);

CREATE TABLE outfit_items (
    id         uuid PRIMARY KEY,
    outfit_id  uuid NOT NULL REFERENCES outfits (id) ON DELETE CASCADE,
    product_id uuid NOT NULL REFERENCES products (id),
    slot       text NOT NULL
);

CREATE TABLE feed_entries (
    id         uuid PRIMARY KEY,
    user_id    uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    outfit_id  uuid NOT NULL REFERENCES outfits (id),
    rank_score numeric(6, 4) NOT NULL,
    created_at timestamp NOT NULL,
    updated_at timestamp,
    shown_at   timestamp
);

CREATE TABLE user_outfit_interactions (
    id               uuid PRIMARY KEY,
    user_id          uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    outfit_id        uuid NOT NULL REFERENCES outfits (id),
    interaction_type text NOT NULL,
    created_at       timestamp NOT NULL,
    updated_at       timestamp,
    CONSTRAINT uq_user_outfit_interactions UNIQUE (user_id, outfit_id, interaction_type)
);

CREATE TABLE ai_prompt_queries (
    id                  uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    raw_prompt          text NOT NULL,
    structured_query    jsonb,
    resulting_outfit_id uuid REFERENCES outfits (id),
    created_at          timestamp NOT NULL,
    updated_at          timestamp
);