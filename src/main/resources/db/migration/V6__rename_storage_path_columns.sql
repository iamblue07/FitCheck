ALTER TABLE user_body_photos RENAME COLUMN file_path TO storage_key;
ALTER TABLE tryon_requests RENAME COLUMN result_image_path TO result_image_storage_key;