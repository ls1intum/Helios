-- Add deployment_workflow_id column to environment table
ALTER TABLE public.environment
ADD COLUMN deployment_workflow_id bigint;

-- Add foreign key constraint
ALTER TABLE public.environment
ADD CONSTRAINT fk_environment_deployment_workflow 
FOREIGN KEY (deployment_workflow_id) 
REFERENCES public.workflow(id) 
ON DELETE SET NULL;

-- First, assign deployment workflows to environments based on type and repository
-- For TEST environments
UPDATE public.environment e
SET deployment_workflow_id = w.id
FROM public.workflow w
WHERE e.type = 'TEST' 
  AND e.repository_id = w.repository_id
  AND w.label = 'DEPLOY_TEST_SERVER'
  AND e.deployment_workflow_id IS NULL;

-- For STAGING environments
UPDATE public.environment e
SET deployment_workflow_id = w.id
FROM public.workflow w
WHERE e.type = 'STAGING' 
  AND e.repository_id = w.repository_id
  AND w.label = 'DEPLOY_STAGING_SERVER'
  AND e.deployment_workflow_id IS NULL;

-- For PRODUCTION environments
UPDATE public.environment e
SET deployment_workflow_id = w.id
FROM public.workflow w
WHERE e.type = 'PRODUCTION' 
  AND e.repository_id = w.repository_id
  AND w.label = 'DEPLOY_PRODUCTION_SERVER'
  AND e.deployment_workflow_id IS NULL;

-- Now update all deployment labels to NONE
UPDATE public.workflow 
SET label = 'NONE' 
WHERE label IN ('DEPLOY_TEST_SERVER', 'DEPLOY_STAGING_SERVER', 'DEPLOY_PRODUCTION_SERVER');

-- Drop the old constraint
ALTER TABLE public.workflow DROP CONSTRAINT IF EXISTS workflow_label_check;

-- Add the new constraint with only TEST and NONE
ALTER TABLE public.workflow ADD CONSTRAINT workflow_label_check 
CHECK (label::text = ANY (ARRAY['NONE'::character varying::text, 'TEST'::character varying::text]));
