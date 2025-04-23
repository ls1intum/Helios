-- Migration to create environment_protection_rule table
CREATE TABLE public.environment_protection_rule (
    id bigint NOT NULL,
    environment_id bigint NOT NULL,
    rule_type character varying(50) NOT NULL,
    -- Required reviewers specific fields
    prevent_self_review boolean,
    reviewers jsonb,
    
    -- Wait timer specific field
    wait_timer integer,
    
    -- Branch policy specific fields
    protected_branches boolean,
    custom_branch_policies boolean,
    allowed_branches jsonb,

    created_at timestamp(6) with time zone DEFAULT now(),
    updated_at timestamp(6) with time zone DEFAULT now(),
    CONSTRAINT pk_environment_protection_rule PRIMARY KEY (id),
    CONSTRAINT fk_environment FOREIGN KEY (environment_id)
        REFERENCES public.environment (id) ON DELETE CASCADE,
    CONSTRAINT unique_rule_per_env UNIQUE (environment_id, rule_type)
);

-- Create index for efficient JSON queries
CREATE INDEX idx_protection_rules_reviewers ON public.environment_protection_rule 
USING GIN (reviewers jsonb_path_ops);

-- Create index for quick lookups by environment
CREATE INDEX idx_protection_rules_environment_id ON public.environment_protection_rule 
(environment_id);

-- Add this index for branch policy lookups
CREATE INDEX idx_protection_rules_branches ON public.environment_protection_rule 
USING GIN (allowed_branches jsonb_path_ops);