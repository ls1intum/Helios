CREATE TABLE test_failure_analysis (
    id BIGSERIAL PRIMARY KEY,
    test_case_id BIGINT NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    result_json TEXT,
    updated_at TIMESTAMPTZ NOT NULL,
    duration_ms BIGINT,
    requester_user_id VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_test_failure_analysis_test_case
        FOREIGN KEY (test_case_id)
            REFERENCES test_case (id)
            ON DELETE CASCADE,
    CONSTRAINT chk_test_failure_analysis_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_test_failure_analysis_requester_created
    ON test_failure_analysis (requester_user_id, created_at);

CREATE INDEX idx_test_failure_analysis_cache_lookup
    ON test_failure_analysis (test_case_id, provider_id, status, updated_at, id);
