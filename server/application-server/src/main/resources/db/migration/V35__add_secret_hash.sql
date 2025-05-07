-- Add secret_hash column to repository_settings table
ALTER TABLE repository_settings
    ADD COLUMN secret_hash TEXT NULL;

-- Add state column to environment_status table
ALTER TABLE public.environment_status
    ADD COLUMN state VARCHAR(255) NULL;

