ALTER TABLE helios_deployment
    ADD COLUMN deploy_job_started_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN deployment_started_at TIMESTAMP WITH TIME ZONE;
