-- Add workflow_run_id column to the helios_deployment table
ALTER TABLE helios_deployment
    ADD COLUMN workflow_run_id BIGINT;

-- Update the new column with values extracted from the workflow_run_html_url column
UPDATE helios_deployment
SET workflow_run_id = CAST(
    substring(workflow_run_html_url from '/runs/([0-9]+)') AS BIGINT
)
WHERE workflow_run_html_url IS NOT NULL;

-- Add foreign key constraint to ensure referential integrity
ALTER TABLE helios_deployment
    ADD CONSTRAINT fk_helios_deployment_workflow_run
    FOREIGN KEY (workflow_run_id) REFERENCES public.workflow_run(id);