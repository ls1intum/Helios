-- The workflow_run_id on helios_deployment is set at dispatch time from the GitHub dispatch
-- response, but the corresponding workflow_run row only arrives later via webhook sync.
-- The FK constraint causes a DataIntegrityViolationException on the deploy request commit.
ALTER TABLE public.helios_deployment
    DROP CONSTRAINT IF EXISTS fk_helios_deployment_workflow_run;
