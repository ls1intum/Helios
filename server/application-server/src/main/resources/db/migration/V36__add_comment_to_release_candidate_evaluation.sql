-- Add comment column to release_candidate_evaluation table
ALTER TABLE release_candidate_evaluation
ADD COLUMN comment VARCHAR(500);