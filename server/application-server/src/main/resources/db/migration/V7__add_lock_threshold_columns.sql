-- Add new columns to the environment table

ALTER TABLE environment
    ADD COLUMN lock_expiration_threshold BIGINT; -- Nullable by default

ALTER TABLE environment
    ADD COLUMN lock_reservation_threshold BIGINT; -- Nullable by default

ALTER TABLE environment
    ADD COLUMN lock_will_expire_at TIMESTAMP WITH TIME ZONE; -- Nullable by default

ALTER TABLE environment
    ADD COLUMN lock_reservation_expires_at TIMESTAMP WITH TIME ZONE; -- Nullable by default

-- Add new columns to the repository_settings table with default values

ALTER TABLE repository_settings
    ADD COLUMN lock_expiration_threshold BIGINT DEFAULT 60; -- Default: 60 (1 hour)

ALTER TABLE repository_settings
    ADD COLUMN lock_reservation_threshold BIGINT DEFAULT 30; -- Default: 30 (30 minutes)