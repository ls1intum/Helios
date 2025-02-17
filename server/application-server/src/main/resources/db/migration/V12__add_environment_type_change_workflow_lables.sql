-- Add type column, set existing rows to 'TEST'
ALTER TABLE environment
ADD COLUMN type VARCHAR(255);

UPDATE environment SET type = 'TEST';

-- Add check constraint that allows null
ALTER TABLE environment
    ADD CONSTRAINT environment_type_check CHECK (
        type::text = ANY (
            ARRAY ['TEST'::varchar, 'STAGING'::varchar, 'PRODUCTION'::varchar]::text []
        )
        OR type IS NULL
    );

-- Drop the old label constraint
ALTER TABLE workflow DROP CONSTRAINT workflow_label_check;

-- Update existing data to match new values and ensure no nulls
UPDATE workflow
SET label = CASE
        WHEN label = 'DEPLOYMENT' THEN 'DEPLOY_TEST_SERVER'
        WHEN label NOT IN ('DEPLOY_TEST_SERVER', 'DEPLOY_STAGING_SERVER', 'DEPLOY_PRODUCTION_SERVER', 'TEST') THEN 'NONE'
        ELSE label
    END;

-- Make label column not null and set default
ALTER TABLE workflow 
    ALTER COLUMN label SET NOT NULL,
    ALTER COLUMN label SET DEFAULT 'NONE';

-- Add new constraint for label with updated values
ALTER TABLE workflow
    ADD CONSTRAINT workflow_label_check CHECK (
        label::text = ANY (
            ARRAY ['NONE'::varchar, 'TEST'::varchar, 'DEPLOY_TEST_SERVER'::varchar, 'DEPLOY_STAGING_SERVER'::varchar, 'DEPLOY_PRODUCTION_SERVER'::varchar]::text []
        )
    );