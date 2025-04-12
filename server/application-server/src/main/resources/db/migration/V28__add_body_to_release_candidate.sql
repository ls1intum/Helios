-- Add body column to release_candidate table to store generated release notes
ALTER TABLE release_candidate
ADD COLUMN body TEXT; 