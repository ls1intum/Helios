-- One row per unique test per repository, replacing the expensive in-memory computation over
-- the full test_case_statistics table on every overview request.
CREATE TABLE test_case_flakiness (
    id                          BIGSERIAL PRIMARY KEY,
    repository_id               BIGINT NOT NULL,
    test_name                   VARCHAR(255) NOT NULL,
    class_name                  VARCHAR(255) NOT NULL,
    test_suite_name             VARCHAR(255) NOT NULL,
    flakiness_score             DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    default_branch_failure_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    combined_failure_rate       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    last_updated                TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_test_case_flakiness
        UNIQUE (repository_id, test_name, class_name, test_suite_name),
    CONSTRAINT fk_test_case_flakiness_repository
        FOREIGN KEY (repository_id) REFERENCES repository (repository_id) ON DELETE CASCADE
);

-- Main query index: fetch all flaky tests for a repo, ordered by score descending.
CREATE INDEX idx_flakiness_repo_score ON test_case_flakiness (repository_id, flakiness_score DESC);

