-- Remove all existing rows from data_sync_status
DELETE
FROM data_sync_status;

-- Add a new column for repository_name_with_owner to tie a sync run to a Git repository by its name
ALTER TABLE data_sync_status ADD COLUMN repository_name_with_owner VARCHAR(255);

-- Add a new column for the sync status
ALTER TABLE data_sync_status
    ADD COLUMN status VARCHAR(255);

-- Add a check constraint to restrict the status column to allowed values
ALTER TABLE data_sync_status
    ADD CONSTRAINT data_sync_status_state_check
        CHECK (status IN ('IN_PROGRESS', 'SUCCESS', 'FAILED'));

