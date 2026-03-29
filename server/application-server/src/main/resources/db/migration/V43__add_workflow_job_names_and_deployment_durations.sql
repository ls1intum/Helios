CREATE TABLE deployment_workflow_config (
    id                   BIGSERIAL PRIMARY KEY,
    workflow_id          BIGINT NOT NULL UNIQUE REFERENCES workflow(id) ON DELETE CASCADE,
    deploy_job_name      VARCHAR(255),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

ALTER TABLE helios_deployment ADD COLUMN build_duration_seconds INTEGER;
ALTER TABLE helios_deployment ADD COLUMN deploy_duration_seconds INTEGER;
