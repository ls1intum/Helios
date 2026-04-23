CREATE TABLE test_failure_analysis (
    id BIGSERIAL PRIMARY KEY,
    test_case_id BIGINT NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    result_json TEXT NOT NULL,
    analyzed_at TIMESTAMPTZ NOT NULL,
    duration_ms BIGINT NOT NULL,
    CONSTRAINT fk_test_failure_analysis_test_case
        FOREIGN KEY (test_case_id)
            REFERENCES test_case (id)
            ON DELETE CASCADE,
    CONSTRAINT uk_test_failure_analysis_cache_key
        UNIQUE (test_case_id, provider_id)
);

CREATE INDEX idx_test_failure_analysis_analyzed_at
    ON test_failure_analysis (analyzed_at);
