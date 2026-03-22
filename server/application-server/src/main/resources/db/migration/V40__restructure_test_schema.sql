-- ============================================================
-- V40: Restructure test schema
--
-- Goals:
--   1. Rename test_suite -> test_suite_run
--   2. Introduce canonical test_case (definition)
--   3. Convert per-run test_case rows into test_case_run
--   4. Migrate test_case_statistics to FK-based
--   5. Absorb test_case_flakiness into test_case
--   6. Drop old tables
-- ============================================================

-- Give the planner enough memory to hold hash-join build sides in RAM,
-- avoiding disk spills during the large backfill joins below.
SET LOCAL work_mem = '256MB';

-- -------------------------------------------------------
-- 1. Rename test_suite -> test_suite_run
-- -------------------------------------------------------
ALTER TABLE test_suite RENAME TO test_suite_run;

-- -------------------------------------------------------
-- 2. Rename old test_case -> test_case_old (free the name)
-- -------------------------------------------------------
ALTER TABLE test_case RENAME TO test_case_old;

-- -------------------------------------------------------
-- 3. Create canonical test_case table
-- -------------------------------------------------------
CREATE TABLE test_case (
                           id                          BIGSERIAL PRIMARY KEY,
                           repository_id               BIGINT NOT NULL REFERENCES repository(repository_id) ON DELETE CASCADE,
                           suite_name                  VARCHAR(500) NOT NULL,
                           class_name                  VARCHAR(255) NOT NULL,
                           name                        VARCHAR(255) NOT NULL,
                           flakiness_score             DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                           default_branch_failure_rate DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                           combined_failure_rate       DOUBLE PRECISION NOT NULL DEFAULT 0.0,
                           updated_at                  TIMESTAMP WITH TIME ZONE,
                           CONSTRAINT uk_test_case_identity UNIQUE (repository_id, suite_name, class_name, name)
);

CREATE INDEX idx_test_case_repo_flakiness ON test_case (repository_id, flakiness_score DESC);

-- -------------------------------------------------------
-- 4. Backfill test_case from all identity sources
-- -------------------------------------------------------

-- 4a. From existing per-run test data
INSERT INTO test_case (repository_id, suite_name, class_name, name)
SELECT DISTINCT wr.repository_id, tsr.name, tco.class_name, tco.name
FROM test_case_old tco
         JOIN test_suite_run tsr ON tsr.id = tco.test_suite_id
         JOIN workflow_run wr ON wr.id = tsr.workflow_run_id
    ON CONFLICT DO NOTHING;

-- 4b. From test_case_statistics (may have tests not in current runs)
INSERT INTO test_case (repository_id, suite_name, class_name, name)
SELECT DISTINCT tcs.repository_id, tcs.test_suite_name, tcs.class_name, tcs.test_name
FROM test_case_statistics tcs
    ON CONFLICT DO NOTHING;

-- 4c. From test_case_flakiness (may have tests not in statistics)
INSERT INTO test_case (repository_id, suite_name, class_name, name)
SELECT DISTINCT tcf.repository_id, tcf.test_suite_name, tcf.class_name, tcf.test_name
FROM test_case_flakiness tcf
    ON CONFLICT DO NOTHING;

-- 4d. Copy flakiness scores into test_case
UPDATE test_case tc
SET flakiness_score             = tcf.flakiness_score,
    default_branch_failure_rate = tcf.default_branch_failure_rate,
    combined_failure_rate       = tcf.combined_failure_rate,
    updated_at                  = tcf.last_updated
    FROM test_case_flakiness tcf
WHERE tc.repository_id = tcf.repository_id
  AND tc.suite_name    = tcf.test_suite_name
  AND tc.class_name    = tcf.class_name
  AND tc.name          = tcf.test_name;

-- -------------------------------------------------------
-- 5. Create test_case_run table
-- -------------------------------------------------------
CREATE TABLE test_case_run (
                               id                BIGSERIAL PRIMARY KEY,
                               test_suite_run_id BIGINT NOT NULL REFERENCES test_suite_run(id) ON DELETE CASCADE,
                               test_case_id      BIGINT NOT NULL REFERENCES test_case(id),
                               time              DOUBLE PRECISION NOT NULL,
                               status            VARCHAR(20) NOT NULL,
                               error_type        VARCHAR(255),
                               message           TEXT,
                               stack_trace       TEXT,
                               system_out        TEXT
);

-- Indexes are intentionally created AFTER the bulk insert below.
-- Building them on an empty table and maintaining them row-by-row during
-- a large INSERT is significantly slower than a single post-insert build.

-- -------------------------------------------------------
-- 6. Backfill test_case_run from old test_case data
-- -------------------------------------------------------

-- Refresh planner statistics on every table involved in the join so the
-- planner can choose hash-join plans instead of nested-loop plans.
-- test_case was just created and populated, so it has no stats yet.
ANALYZE test_case;
ANALYZE test_case_old;
ANALYZE test_suite_run;
ANALYZE workflow_run;

INSERT INTO test_case_run (test_suite_run_id, test_case_id, time, status, error_type, message, stack_trace, system_out)
SELECT
    tco.test_suite_id,
    tc.id,
    tco.time,
    CASE tco.status
        WHEN 'PASSED'  THEN 'PASSED'
        WHEN 'FAILED'  THEN 'FAILED'
        WHEN 'ERROR'   THEN 'ERROR'
        WHEN 'SKIPPED' THEN 'SKIPPED'
        ELSE 'PASSED'
        END,
    tco.error_type,
    tco.message,
    tco.stack_trace,
    CASE WHEN tco.status IN ('FAILED', 'ERROR') THEN tco.system_out ELSE NULL END
FROM test_case_old tco
         JOIN test_suite_run tsr ON tsr.id = tco.test_suite_id
         JOIN workflow_run wr ON wr.id = tsr.workflow_run_id
         JOIN test_case tc
              ON tc.repository_id = wr.repository_id
                  AND tc.suite_name    = tsr.name
                  AND tc.class_name    = tco.class_name
                  AND tc.name          = tco.name;

-- Build secondary indexes after the bulk load.
CREATE INDEX idx_test_case_run_suite ON test_case_run(test_suite_run_id);
CREATE INDEX idx_test_case_run_case  ON test_case_run(test_case_id);

-- -------------------------------------------------------
-- 7. Migrate test_case_statistics to FK-based
-- -------------------------------------------------------
ALTER TABLE test_case_statistics ADD COLUMN test_case_id BIGINT;

UPDATE test_case_statistics tcs
SET test_case_id = tc.id
    FROM test_case tc
WHERE tc.repository_id = tcs.repository_id
  AND tc.suite_name    = tcs.test_suite_name
  AND tc.class_name    = tcs.class_name
  AND tc.name          = tcs.test_name;

-- Remove orphaned statistics rows that couldn't be matched
DELETE FROM test_case_statistics WHERE test_case_id IS NULL;

ALTER TABLE test_case_statistics ALTER COLUMN test_case_id SET NOT NULL;
ALTER TABLE test_case_statistics
    ADD CONSTRAINT fk_test_case_statistics_test_case
        FOREIGN KEY (test_case_id) REFERENCES test_case(id) ON DELETE CASCADE;

-- Drop old string-based constraints and columns
ALTER TABLE test_case_statistics DROP CONSTRAINT IF EXISTS uk_test_case_statistics;
DROP INDEX IF EXISTS idx_test_case_statistics;
DROP INDEX IF EXISTS idx_branch_name;
DROP INDEX IF EXISTS idx_is_flaky;

ALTER TABLE test_case_statistics DROP COLUMN test_name;
ALTER TABLE test_case_statistics DROP COLUMN class_name;
ALTER TABLE test_case_statistics DROP COLUMN test_suite_name;
ALTER TABLE test_case_statistics DROP COLUMN repository_id;

-- Add new FK-based constraints
ALTER TABLE test_case_statistics
    ADD CONSTRAINT uk_test_case_statistics_new UNIQUE (test_case_id, branch_name);

CREATE INDEX idx_test_case_statistics_case ON test_case_statistics(test_case_id);
CREATE INDEX idx_test_case_statistics_branch ON test_case_statistics(branch_name);

-- -------------------------------------------------------
-- 8. Drop old tables
-- -------------------------------------------------------
DROP TABLE test_case_old CASCADE;
DROP TABLE test_case_flakiness CASCADE;
