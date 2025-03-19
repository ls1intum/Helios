-- Add nullable new column to workflow_run table
-- This column will be used to store the id of the workflow run that triggered the current workflow run
-- Example: If workflow run A triggers workflow run B, then the triggered_workflow_run_id of workflow run B will be A's id
ALTER TABLE workflow_run
ADD COLUMN triggered_workflow_run_id BIGINT;
