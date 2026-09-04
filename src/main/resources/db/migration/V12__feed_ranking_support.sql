ALTER TABLE outfits ADD COLUMN color_score numeric(6, 4);
ALTER TABLE outfits ADD COLUMN layering_score numeric(6, 4);
ALTER TABLE outfits ADD COLUMN structured_score numeric(6, 4);
ALTER TABLE outfits ADD COLUMN embedding_score numeric(6, 4);

UPDATE outfits
SET color_score      = COALESCE(compatibility_score, 0),
    layering_score   = COALESCE(compatibility_score, 0),
    structured_score = COALESCE(compatibility_score, 0),
    embedding_score  = COALESCE(compatibility_score, 0)
WHERE color_score IS NULL;

ALTER TABLE outfits ALTER COLUMN color_score SET NOT NULL;
ALTER TABLE outfits ALTER COLUMN layering_score SET NOT NULL;
ALTER TABLE outfits ALTER COLUMN structured_score SET NOT NULL;
ALTER TABLE outfits ALTER COLUMN embedding_score SET NOT NULL;

ALTER TABLE feed_entries ADD CONSTRAINT uq_feed_entries_user_outfit UNIQUE (user_id, outfit_id);

CREATE INDEX idx_feed_entries_user_rank_unseen ON feed_entries (user_id, rank_score DESC, id DESC)
    WHERE shown_at IS NULL;