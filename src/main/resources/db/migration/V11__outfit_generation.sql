ALTER TABLE products ADD COLUMN garment_role text;
ALTER TABLE outfits ADD COLUMN item_set_hash text NOT NULL UNIQUE;
