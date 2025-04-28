/* -------------------------------------------------------------
 * Make helios_deployment.workflow_run_id cascade on delete.
 *
 * 1. Drop the old FK (if present).
 * 2. Re-add it with ON DELETE CASCADE.
 * 3. Add NOT VALID / VALIDATE so that the table is locked only
 *    for a very brief moment (PostgreSQL 12+).
 * ----------------------------------------------------------- */

-- drop the constraint if it already exists
ALTER TABLE public.helios_deployment DROP CONSTRAINT IF EXISTS fk_helios_deployment_workflow_run;

-- add the same FK with ON DELETE CASCADE
ALTER TABLE public.helios_deployment
    ADD CONSTRAINT fk_helios_deployment_workflow_run
        FOREIGN KEY (workflow_run_id)
            REFERENCES public.workflow_run(id)
            ON DELETE CASCADE;