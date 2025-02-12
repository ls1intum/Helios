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

-- Modify environment_lock_history table
ALTER TABLE environment_lock_history 
    RENAME COLUMN author_id TO locking_author_id;

ALTER TABLE environment_lock_history
    ADD COLUMN unlocking_author_id BIGINT;

-- Update existing records: set unlocking_author_id = locking_author_id where unlocked_at exists
UPDATE environment_lock_history
SET unlocking_author_id = locking_author_id
WHERE unlocked_at IS NOT NULL;

-- Add foreign key constraints
ALTER TABLE environment_lock_history
    ADD CONSTRAINT fk_environment_lock_history_locking_author
    FOREIGN KEY (locking_author_id) REFERENCES "user"(id);

ALTER TABLE environment_lock_history
    ADD CONSTRAINT fk_environment_lock_history_unlocking_author
    FOREIGN KEY (unlocking_author_id) REFERENCES "user"(id);