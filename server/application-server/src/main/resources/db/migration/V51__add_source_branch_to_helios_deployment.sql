ALTER TABLE public.helios_deployment
    ADD COLUMN source_branch_name VARCHAR(255);

UPDATE public.helios_deployment
SET source_branch_name = COALESCE(workflow_params ->> 'branch_name', branch_name)
WHERE source_branch_name IS NULL;
