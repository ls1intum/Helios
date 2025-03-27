-- Fill in null names with temporary unique placeholders
UPDATE public.workflow_group
SET name = CONCAT('TEMP_NAME_', id)
WHERE name IS NULL;

-- Make the name column NOT NULL
ALTER TABLE public.workflow_group
    ALTER COLUMN name SET NOT NULL;
