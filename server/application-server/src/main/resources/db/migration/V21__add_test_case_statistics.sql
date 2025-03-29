-- Add test case statistics table for flaky test detection
CREATE TABLE test_case_statistics (
    id BIGSERIAL PRIMARY KEY,
    test_name VARCHAR(255) NOT NULL,
    class_name VARCHAR(255) NOT NULL,
    test_suite_name VARCHAR(255) NOT NULL,
    branch_name VARCHAR(255) NOT NULL,
    total_runs INTEGER NOT NULL DEFAULT 0,
    failed_runs INTEGER NOT NULL DEFAULT 0,
    failure_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    is_flaky BOOLEAN NOT NULL DEFAULT FALSE,
    last_updated TIMESTAMP WITH TIME ZONE NOT NULL,
    
    CONSTRAINT uk_test_case_statistics UNIQUE (test_name, class_name, test_suite_name, branch_name)
);

-- Add indexes for efficient querying
CREATE INDEX idx_test_case_statistics ON test_case_statistics (test_name, class_name, test_suite_name, branch_name);
CREATE INDEX idx_branch_name ON test_case_statistics (branch_name);
CREATE INDEX idx_is_flaky ON test_case_statistics (branch_name, is_flaky);

-- Add index for efficient workflow run querying for statistics migration
CREATE INDEX idx_workflow_run_branch_repo_status ON workflow_run (head_branch, repository_id, test_processing_status); 