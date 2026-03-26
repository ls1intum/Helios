-- Backfill flakiness scores from test_case_statistics into test_case_flakiness.
-- Formula mirrors TestCaseStatisticsService.calculateFlakinessScore:
--   default_flakiness  = (0.5 - default_rate) / 0.5   if default_rate  in (0.01, 0.5), else 0
--   combined_flakiness = (0.5 - combined_rate) / 0.5  if combined_rate in (0.01, 0.5), else 0
--   flakiness_score    = (default_flakiness * 0.7 + combined_flakiness * 0.3) * 100
INSERT INTO test_case_flakiness (
    repository_id,
    test_name,
    class_name,
    test_suite_name,
    flakiness_score,
    default_branch_failure_rate,
    combined_failure_rate,
    last_updated
)
WITH combined_stats AS (
    SELECT
        repository_id,
        test_name,
        class_name,
        test_suite_name,
        CASE
            WHEN total_runs > 0 THEN failed_runs::DOUBLE PRECISION / total_runs
            ELSE 0.0
        END AS failure_rate,
        last_updated
    FROM test_case_statistics
    WHERE branch_name = 'combined'
),
default_stats AS (
    SELECT
        tcs.repository_id,
        tcs.test_name,
        tcs.class_name,
        tcs.test_suite_name,
        CASE
            WHEN tcs.total_runs > 0 THEN tcs.failed_runs::DOUBLE PRECISION / tcs.total_runs
            ELSE 0.0
        END AS failure_rate
    FROM test_case_statistics tcs
    JOIN branch b
        ON b.repository_id = tcs.repository_id
       AND b.is_default = TRUE
       AND tcs.branch_name = b.name
),
all_tests AS (
    -- Tests that have a combined row (normal case; combined is written by ingestion).
    SELECT
        c.repository_id,
        c.test_name,
        c.class_name,
        c.test_suite_name,
        COALESCE(d.failure_rate, 0.0) AS default_rate,
        c.failure_rate AS combined_rate,
        c.last_updated
    FROM combined_stats c
    LEFT JOIN default_stats d
        ON d.repository_id = c.repository_id
       AND d.test_name = c.test_name
       AND d.class_name = c.class_name
       AND d.test_suite_name = c.test_suite_name

    UNION ALL

    -- Tests that only exist on the default branch (no combined row yet).
    SELECT
        d.repository_id,
        d.test_name,
        d.class_name,
        d.test_suite_name,
        d.failure_rate AS default_rate,
        0.0 AS combined_rate,
        CURRENT_TIMESTAMP AS last_updated
    FROM default_stats d
    LEFT JOIN combined_stats c
        ON c.repository_id = d.repository_id
       AND c.test_name = d.test_name
       AND c.class_name = d.class_name
       AND c.test_suite_name = d.test_suite_name
    WHERE c.test_name IS NULL
),
scored AS (
    SELECT
        repository_id,
        test_name,
        class_name,
        test_suite_name,
        default_rate,
        combined_rate,
        last_updated,
        CASE
            WHEN default_rate > 0.01 AND default_rate < 0.5 THEN (0.5 - default_rate) / 0.5
            ELSE 0.0
        END AS default_flakiness,
        CASE
            WHEN combined_rate > 0.01 AND combined_rate < 0.5 THEN (0.5 - combined_rate) / 0.5
            ELSE 0.0
        END AS combined_flakiness
    FROM all_tests
)
SELECT
    repository_id,
    test_name,
    class_name,
    test_suite_name,
    (default_flakiness * 0.7 + combined_flakiness * 0.3) * 100 AS flakiness_score,
    default_rate AS default_branch_failure_rate,
    combined_rate AS combined_failure_rate,
    last_updated
FROM scored
ON CONFLICT (repository_id, test_name, class_name, test_suite_name)
DO UPDATE SET
    flakiness_score = EXCLUDED.flakiness_score,
    default_branch_failure_rate = EXCLUDED.default_branch_failure_rate,
    combined_failure_rate = EXCLUDED.combined_failure_rate,
    last_updated = EXCLUDED.last_updated;
