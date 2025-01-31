-- Add environment_type column with check constraint
ALTER TABLE environment
ADD COLUMN environment_type VARCHAR(255),
    ADD CONSTRAINT environment_type_check CHECK (
        environment_type::text = ANY (
            ARRAY ['TEST'::varchar, 'STAGING'::varchar, 'PRODUCTION'::varchar]::text []
        )
    );
-- Add deployment_environment column with check constraint
ALTER TABLE workflow
ADD COLUMN deployment_environment VARCHAR(255) DEFAULT 'NONE',
    ADD CONSTRAINT workflow_deployment_environment_check CHECK (
        deployment_environment::text = ANY (
            ARRAY ['NONE'::varchar, 'TEST_SERVER'::varchar, 'STAGING_SERVER'::varchar, 'PRODUCTION_SERVER'::varchar]::text []
        )
    );
-- Set default value for environment_type
UPDATE environment
SET environment_type = 'TEST';
-- Make environment_type NOT NULL after setting default values
ALTER TABLE environment
ALTER COLUMN environment_type
SET NOT NULL;
-- Migrate workflow.label to workflow.deployment_environment
UPDATE workflow
SET deployment_environment = CASE
        WHEN label = 'BUILD' THEN 'NONE'
        WHEN label = 'DEPLOYMENT' THEN 'TEST_SERVER'
        WHEN label = 'NONE' THEN 'NONE'
    END;
-- Drop the old label column and its constraint
ALTER TABLE workflow DROP CONSTRAINT workflow_label_check,
    DROP COLUMN label;