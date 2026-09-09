CREATE TABLE ai_prompt_query_outfits (
                                         id                 uuid PRIMARY KEY,
                                         ai_prompt_query_id uuid NOT NULL REFERENCES ai_prompt_queries (id) ON DELETE CASCADE,
                                         outfit_id          uuid NOT NULL REFERENCES outfits (id),
                                         rank               integer NOT NULL,
                                         CONSTRAINT uq_ai_prompt_query_outfits UNIQUE (ai_prompt_query_id, outfit_id)
);

INSERT INTO ai_prompt_query_outfits (id, ai_prompt_query_id, outfit_id, rank)
SELECT gen_random_uuid(), id, resulting_outfit_id, 0
FROM ai_prompt_queries
WHERE resulting_outfit_id IS NOT NULL;

ALTER TABLE ai_prompt_queries DROP COLUMN resulting_outfit_id;

ALTER TABLE ai_prompt_queries ADD COLUMN match_profile boolean NOT NULL DEFAULT true;
ALTER TABLE ai_prompt_queries ALTER COLUMN match_profile DROP DEFAULT;