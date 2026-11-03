-- Helios was cut down to its core (GitHub Actions UI + deployments to test servers).
-- Drop the schema of the removed features: AI test-failure analysis, test-result ingestion and
-- flaky-test statistics, and release management (releases, release candidates, evaluations).
-- No remaining JPA entity maps any of these tables or columns.

-- AI test-failure analysis cache (FK -> test_case)
DROP TABLE IF EXISTS public.test_failure_analysis CASCADE;

-- Flaky tests / test statistics
DROP TABLE IF EXISTS public.test_case_flakiness CASCADE;
DROP TABLE IF EXISTS public.test_case_statistics CASCADE;

-- Test results (children first; CASCADE also drops their indexes and FKs)
DROP TABLE IF EXISTS public.test_case CASCADE;
DROP TABLE IF EXISTS public.test_suite CASCADE;
DROP TABLE IF EXISTS public.test_type CASCADE;

-- Release management
DROP TABLE IF EXISTS public.release_candidate_evaluation CASCADE;
DROP TABLE IF EXISTS public.release_candidate CASCADE;
DROP TABLE IF EXISTS public.release CASCADE;

-- Test-processing status on workflow runs (also drops idx_workflow_run_branch_repo_status and the
-- workflow_run_test_processing_status_check constraint)
ALTER TABLE public.workflow_run DROP COLUMN IF EXISTS test_processing_status;

-- Package name used only to filter test-result logs
ALTER TABLE public.repository_settings DROP COLUMN IF EXISTS package_name;

-- Latest release tag shown on the repository overview (release management is gone)
ALTER TABLE public.repository DROP COLUMN IF EXISTS latest_release_tag_name;
