-- ---------------------------------------------------------------------------------------
-- Migration Script: Data Sync Status Enhancements
--
-- Summary:
-- This Flyway migration script enhances the `data_sync_status` table to better track
-- synchronization runs. The script performs the following actions:
--
-- 1. Removes all existing rows from the table to start with a clean slate.
-- 2. Adds a new column `repository_name_with_owner` to store the Git repository name (including the owner)
--    associated with each sync run.
-- 3. Adds a new column `status` to record the current synchronization state.
-- 4. Applies a check constraint on the `status` column to restrict its values to only:
--    'IN_PROGRESS', 'SUCCESS', or 'FAILED', ensuring that only valid statuses are recorded.
--
-- ---------------------------------------------------------------------------------------


-- Remove all existing rows from the data_sync_status table
DELETE
FROM data_sync_status;

-- Add a new column to store the repository name with owner
ALTER TABLE data_sync_status ADD COLUMN repository_name_with_owner VARCHAR(255);

-- Add a new column to store the current synchronization status
ALTER TABLE data_sync_status
    ADD COLUMN status VARCHAR(255);

-- Add a check constraint to restrict the status column to valid values
ALTER TABLE data_sync_status
    ADD CONSTRAINT data_sync_status_state_check
        CHECK (status IN ('IN_PROGRESS', 'SUCCESS', 'FAILED'));

