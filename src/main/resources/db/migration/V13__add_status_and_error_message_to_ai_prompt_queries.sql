ALTER TABLE ai_prompt_queries ADD COLUMN status text NOT NULL;
ALTER TABLE ai_prompt_queries ADD COLUMN error_message text;