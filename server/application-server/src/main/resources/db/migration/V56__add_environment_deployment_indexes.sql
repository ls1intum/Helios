-- Indexes for the environments "enabled" hot path: findLatestDeployment() and the
-- release-candidate-by-(repository, commit_sha) lookup ran sequential scans per environment,
-- which pinned application-server CPU under load. Created live on prod during the 2026-07-03
-- incident and codified here. IF NOT EXISTS makes this a no-op where they already exist and
-- creates them on staging / other environments.
CREATE INDEX IF NOT EXISTS idx_deployment_env_created
    ON deployment (environment_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_helios_deployment_env_created
    ON helios_deployment (environment_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_release_candidate_repo_sha
    ON release_candidate (repository_id, commit_sha);
