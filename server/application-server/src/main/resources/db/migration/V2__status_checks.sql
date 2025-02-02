-- Add new columns to environment table
ALTER TABLE public.environment 
  ADD COLUMN status_url VARCHAR(255),
  ADD COLUMN status_check_type VARCHAR(20);

-- Create environment_status table
CREATE TABLE public.environment_status (
  id BIGSERIAL PRIMARY KEY,
  environment_id BIGINT NOT NULL REFERENCES public.environment(id) ON DELETE CASCADE,
  success BOOLEAN NOT NULL,
  http_status_code INT NOT NULL,
  check_type VARCHAR(20) NOT NULL,
  check_timestamp TIMESTAMP NOT NULL,
  metadata JSONB
);

-- Create indexes for common query patterns
CREATE INDEX idx_env_status_env ON public.environment_status(environment_id);
CREATE INDEX idx_env_status_order ON public.environment_status(environment_id, check_timestamp DESC);
CREATE INDEX idx_env_status_type ON public.environment_status(check_type);