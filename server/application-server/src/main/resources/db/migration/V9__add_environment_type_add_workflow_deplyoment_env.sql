-- Add type column with check constraint
ALTER TABLE environment
ADD COLUMN type VARCHAR(255) DEFAULT 'TEST' NOT NULL,
    ADD CONSTRAINT environment_type_check CHECK (
        type::text = ANY (
            ARRAY ['TEST'::varchar, 'STAGING'::varchar, 'PRODUCTION'::varchar]::text []
        )
    );

-- Drop the old label constraint
ALTER TABLE workflow DROP CONSTRAINT workflow_label_check;

-- Add new constraint for label with updated values
ALTER TABLE workflow
    ADD CONSTRAINT workflow_label_check CHECK (
        label::text = ANY (
            ARRAY ['NONE'::varchar, 'TEST_SERVER'::varchar, 'STAGING_SERVER'::varchar, 'PRODUCTION_SERVER'::varchar]::text []
        )
    );

-- Update existing data to match new values
UPDATE workflow
SET label = CASE
        WHEN label = 'BUILD' THEN 'NONE'
        WHEN label = 'DEPLOYMENT' THEN 'TEST_SERVER'
        WHEN label = 'NONE' THEN 'NONE'
    END;

-- Make sure label has a default value
ALTER TABLE workflow 
ALTER COLUMN label SET DEFAULT 'NONE';