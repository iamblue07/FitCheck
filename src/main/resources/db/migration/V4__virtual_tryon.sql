CREATE TABLE tryon_requests (
    id                uuid PRIMARY KEY,
    user_id           uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    outfit_id         uuid REFERENCES outfits (id),
    status            text NOT NULL,
    result_image_path text,
    error_message     text,
    created_at        timestamp NOT NULL,
    updated_at        timestamp,
    completed_at      timestamp
);

CREATE TABLE tryon_request_items (
    id                uuid PRIMARY KEY,
    tryon_request_id  uuid NOT NULL REFERENCES tryon_requests (id) ON DELETE CASCADE,
    product_id        uuid NOT NULL REFERENCES products (id),
    sequence_order    integer NOT NULL,
    status            text NOT NULL
);