CREATE TABLE environment_required_pre_deployment_workflow (
    environment_id BIGINT NOT NULL,
    workflow_id BIGINT NOT NULL,
    PRIMARY KEY (environment_id, workflow_id),
    CONSTRAINT fk_env_required_pre_deploy_workflow_environment
        FOREIGN KEY (environment_id)
            REFERENCES environment (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_env_required_pre_deploy_workflow_workflow
        FOREIGN KEY (workflow_id)
            REFERENCES workflow (id)
            ON DELETE CASCADE
);
