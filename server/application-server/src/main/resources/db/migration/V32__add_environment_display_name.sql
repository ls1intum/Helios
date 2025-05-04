ALTER TABLE environment
ADD COLUMN display_name VARCHAR(255);

-- Initialize display_name with name only where display_name is null
UPDATE environment SET display_name = name WHERE display_name IS NULL;