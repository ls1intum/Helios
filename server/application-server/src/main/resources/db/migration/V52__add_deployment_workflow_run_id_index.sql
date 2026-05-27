-- Most legacy deployment rows have a NULL workflow_run_id; a partial index
-- keeps the on-disk footprint small while still serving the orphan-branch
-- cleanup's NOT EXISTS subquery against deployment.workflow_run_id.
CREATE INDEX idx_deployment_workflow_run_id
    ON deployment(workflow_run_id)
    WHERE workflow_run_id IS NOT NULL;
