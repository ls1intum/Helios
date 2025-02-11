-- V2__Add_Environment_Type_And_Deployment_Environment.sql
-- Add type column with check constraint
ALTER TABLE environment
ADD COLUMN type VARCHAR(255) DEFAULT 'TEST' NOT NULL,
    ADD CONSTRAINT environment_type_check CHECK (
        type::text = ANY (
            ARRAY ['TEST'::varchar, 'STAGING'::varchar, 'PRODUCTION'::varchar]::text []
        )
    );
-- Add deployment_environment column
ALTER TABLE workflow
ADD COLUMN deployment_environment VARCHAR(255) DEFAULT 'NONE',
    ADD CONSTRAINT workflow_deployment_environment_check CHECK (
        deployment_environment::text = ANY (
            ARRAY ['NONE'::varchar, 'TEST_SERVER'::varchar, 'STAGING_SERVER'::varchar, 'PRODUCTION_SERVER'::varchar]::text []
        )
    );
-- Migrate data from label to deployment_environment
UPDATE workflow
SET deployment_environment = CASE
        WHEN label = 'BUILD' THEN 'NONE'
        WHEN label = 'DEPLOYMENT' THEN 'TEST_SERVER'
        WHEN label = 'NONE' THEN 'NONE'
    END;
-- Make deployment_environment NOT NULL after data migration
ALTER TABLE workflow
ALTER COLUMN deployment_environment
SET NOT NULL;
-- Drop the old label column and its constraint
ALTER TABLE workflow DROP CONSTRAINT workflow_label_check CASCADE;
ALTER TABLE workflow DROP COLUMN label;