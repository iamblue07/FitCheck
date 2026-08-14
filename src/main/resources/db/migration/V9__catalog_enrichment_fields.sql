ALTER TABLE products ADD COLUMN occasion text;
ALTER TABLE products ADD COLUMN primary_color text;
ALTER TABLE products ADD COLUMN secondary_color text;
ALTER TABLE products ADD COLUMN layering_role text;
ALTER TABLE products ALTER COLUMN text_embedding TYPE vector(3072);