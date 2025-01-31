-- Add new columns to environment table
ALTER TABLE environment 
  ADD COLUMN status_url VARCHAR(255),
  ADD COLUMN status_check_type VARCHAR(20);

-- Create environment_status table
CREATE TABLE environment_status (
  id BIGSERIAL PRIMARY KEY,
  environment_id BIGINT NOT NULL REFERENCES environment(id) ON DELETE CASCADE,
  success BOOLEAN NOT NULL,
  status_code INT,
  check_type VARCHAR(20) NOT NULL,
  check_timestamp TIMESTAMP NOT NULL,
  metadata JSONB
);

-- Create indexes for common query patterns
CREATE INDEX idx_env_status_env ON environment_status(environment_id);
CREATE INDEX idx_env_status_order ON environment_status(environment_id, check_timestamp DESC);
CREATE INDEX idx_env_status_type ON environment_status(check_type);