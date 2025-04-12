-- Migration script to remove is_flaky and failure_rate columns from test_case_statistics
ALTER TABLE public.test_case_statistics DROP COLUMN IF EXISTS is_flaky;
ALTER TABLE public.test_case_statistics DROP COLUMN IF EXISTS failure_rate; 