CREATE TABLE products (
    id                   uuid PRIMARY KEY,
    external_id          text NOT NULL,
    gender               text,
    master_category      text,
    sub_category         text,
    article_type         text,
    base_colour          text,
    season               text,
    year                 integer,
    usage                text,
    product_display_name text,
    fit                  text,
    silhouette           text,
    pattern              text,
    material_guess       text,
    formality            text,
    description          text,
    text_embedding       vector,
    image_url            text,
    base_price           numeric(10, 2),
    currency             text,
    created_at           timestamp NOT NULL,
    updated_at           timestamp
);

CREATE TABLE product_style_tags (
    id           uuid PRIMARY KEY,
    product_id   uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    style_tag_id uuid NOT NULL REFERENCES style_tags (id),
    CONSTRAINT uq_product_style_tags UNIQUE (product_id, style_tag_id)
);

CREATE TABLE product_variants (
    id             uuid PRIMARY KEY,
    product_id     uuid NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    size           text NOT NULL,
    stock_quantity integer NOT NULL,
    version        integer NOT NULL DEFAULT 0
);