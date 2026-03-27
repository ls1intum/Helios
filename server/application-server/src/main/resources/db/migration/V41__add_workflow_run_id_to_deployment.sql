ALTER TABLE public.deployment
    ADD COLUMN IF NOT EXISTS workflow_run_id bigint;
