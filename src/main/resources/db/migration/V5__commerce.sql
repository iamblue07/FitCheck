CREATE TABLE orders (
    id           uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES users (id),
    status       text NOT NULL,
    total_amount numeric(10, 2) NOT NULL,
    currency     text NOT NULL,
    created_at   timestamp NOT NULL,
    updated_at   timestamp
);

CREATE TABLE order_items (
    id                     uuid PRIMARY KEY,
    order_id               uuid NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_variant_id     uuid NOT NULL REFERENCES product_variants (id),
    outfit_id              uuid REFERENCES outfits (id),
    quantity               integer NOT NULL,
    unit_price_at_purchase numeric(10, 2) NOT NULL
);