-- Add deployment_workflow_id column to environment table
ALTER TABLE public.environment
ADD COLUMN deployment_workflow_id bigint;

-- Add foreign key constraint
ALTER TABLE public.environment
ADD CONSTRAINT fk_environment_deployment_workflow 
FOREIGN KEY (deployment_workflow_id) 
REFERENCES public.workflow(id) 
ON DELETE SET NULL;