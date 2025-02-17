-- Add pull_request_id column to the helios_deployment table
ALTER TABLE helios_deployment
    ADD COLUMN pull_request_id BIGINT;

-- Add foreign key constraint to ensure referential integrity
ALTER TABLE helios_deployment
    ADD CONSTRAINT fk_helios_deployment_pull_request
    FOREIGN KEY (pull_request_id) REFERENCES public.issue(id);
