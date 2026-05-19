-- =====================================================================
-- V51: Queue monitoring schema (workflow_job, runner, queue_wait_stat,
-- queue_alert_rule, queue_alert_event). See plan §A.
-- =====================================================================

-- Extend the notification_preference CHECK constraint from V34 so the 3
-- new enum values can be persisted (QUEUE_P95_BREACH, RUNNER_OFFLINE,
-- STUCK_JOBS).
ALTER TABLE public.notification_preference
    DROP CONSTRAINT IF EXISTS chk_notification_type;
ALTER TABLE public.notification_preference
    ADD CONSTRAINT chk_notification_type
        CHECK (type IN (
                        'DEPLOYMENT_FAILED',
                        'LOCK_EXPIRED',
                        'LOCK_UNLOCKED',
                        'QUEUE_P95_BREACH',
                        'RUNNER_OFFLINE',
                        'STUCK_JOBS'
            ));

-- ---------------------------------------------------------------------
-- workflow_job: durable row per GitHub Actions job. Today this data is
-- dropped for non-deployment jobs.
-- ---------------------------------------------------------------------
CREATE TABLE workflow_job (
    id                          BIGINT PRIMARY KEY,
    workflow_run_id             BIGINT NOT NULL,
    repository_id               BIGINT NOT NULL,
    name                        VARCHAR(512) NOT NULL,
    workflow_name               VARCHAR(512),
    head_branch                 VARCHAR(512),
    head_sha                    CHAR(40),
    status                      VARCHAR(32) NOT NULL,
    conclusion                  VARCHAR(32),
    created_at                  TIMESTAMPTZ,
    started_at                  TIMESTAMPTZ,
    completed_at                TIMESTAMPTZ,
    queue_wait_seconds          INT,
    run_duration_seconds        INT,
    labels                      TEXT[] NOT NULL DEFAULT '{}',
    label_set_hash              CHAR(64),
    runner_id                   BIGINT,
    runner_name                 VARCHAR(255),
    runner_group_id             BIGINT,
    runner_group_name           VARCHAR(255),
    runner_kind                 VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    queued_reason               VARCHAR(32),
    is_stuck                    BOOLEAN NOT NULL DEFAULT FALSE,
    stuck_detected_at           TIMESTAMPTZ,
    last_reconcile_attempt_at   TIMESTAMPTZ,
    CONSTRAINT fk_workflow_job_repository
        FOREIGN KEY (repository_id)
            REFERENCES repository (repository_id)
            ON DELETE CASCADE,
    CONSTRAINT chk_workflow_job_runner_kind
        CHECK (runner_kind IN ('GITHUB_HOSTED', 'SELF_HOSTED', 'UNKNOWN')),
    CONSTRAINT chk_workflow_job_queued_reason
        CHECK (queued_reason IS NULL OR queued_reason IN (
            'NO_RUNNER_ONLINE', 'RUNNERS_BUSY', 'CONCURRENCY_LOCK',
            'PENDING_APPROVAL', 'UNKNOWN'))
);

CREATE INDEX idx_workflow_job_repo_status
    ON workflow_job (repository_id, status);
CREATE INDEX idx_workflow_job_repo_created_at
    ON workflow_job (repository_id, created_at DESC);
CREATE INDEX idx_workflow_job_workflow_run_id
    ON workflow_job (workflow_run_id);
CREATE INDEX idx_workflow_job_labels_gin
    ON workflow_job USING GIN (labels);
CREATE INDEX idx_workflow_job_label_set_hash
    ON workflow_job (label_set_hash);
CREATE INDEX idx_workflow_job_queued
    ON workflow_job (repository_id, created_at)
    WHERE status = 'queued';

-- ---------------------------------------------------------------------
-- runner: self-hosted runner inventory (org-scoped today, see §A).
-- ---------------------------------------------------------------------
CREATE TABLE runner (
    id                  BIGINT PRIMARY KEY,
    name                VARCHAR(255),
    os                  VARCHAR(32),
    runner_group_id     BIGINT,
    runner_group_name   VARCHAR(255),
    status              VARCHAR(16) NOT NULL DEFAULT 'OFFLINE',
    busy                BOOLEAN NOT NULL DEFAULT FALSE,
    labels              TEXT[] NOT NULL DEFAULT '{}',
    current_job_id      BIGINT,
    last_seen_at        TIMESTAMPTZ,
    first_registered_at TIMESTAMPTZ,
    offline_since       TIMESTAMPTZ,
    CONSTRAINT chk_runner_status
        CHECK (status IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT fk_runner_current_job
        FOREIGN KEY (current_job_id)
            REFERENCES workflow_job (id)
            ON DELETE SET NULL
);

CREATE INDEX idx_runner_status ON runner (status);
CREATE INDEX idx_runner_labels_gin ON runner USING GIN (labels);

-- ---------------------------------------------------------------------
-- queue_wait_stat: pre-aggregated hourly buckets for 7/30-day rolls.
-- ---------------------------------------------------------------------
-- All natural-key parts are NOT NULL to keep ON CONFLICT dedup correct;
-- callers normalize nullable fields to '' before insert (see rollup SQL).
CREATE TABLE queue_wait_stat (
    id              BIGSERIAL PRIMARY KEY,
    repository_id   BIGINT NOT NULL,
    workflow_name   VARCHAR(512) NOT NULL DEFAULT '',
    job_name        VARCHAR(512) NOT NULL DEFAULT '',
    head_branch     VARCHAR(512) NOT NULL DEFAULT '',
    label_set_hash  CHAR(64)     NOT NULL DEFAULT '',
    bucket_start    TIMESTAMPTZ NOT NULL,
    samples         INT NOT NULL,
    queue_p50       INT,
    queue_p90       INT,
    queue_p95       INT,
    run_p50         INT,
    run_p90         INT,
    run_p95         INT,
    CONSTRAINT fk_queue_wait_stat_repository
        FOREIGN KEY (repository_id)
            REFERENCES repository (repository_id)
            ON DELETE CASCADE,
    CONSTRAINT uq_queue_wait_stat_natural
        UNIQUE (repository_id, workflow_name, job_name, head_branch,
                label_set_hash, bucket_start)
);

CREATE INDEX idx_queue_wait_stat_repo_bucket
    ON queue_wait_stat (repository_id, bucket_start DESC);

-- ---------------------------------------------------------------------
-- queue_alert_rule: SLO config. quiet_hours_cron required for
-- RUNNER_OFFLINE_OVER (see plan §I.9) to avoid overnight noise.
-- ---------------------------------------------------------------------
CREATE TABLE queue_alert_rule (
    id                  BIGSERIAL PRIMARY KEY,
    kind                VARCHAR(32) NOT NULL,
    threshold_seconds   INT,
    window_minutes      INT NOT NULL DEFAULT 5,
    repository_id       BIGINT,
    label_set_hash      CHAR(64),
    channels            TEXT[] NOT NULL DEFAULT '{EMAIL}',
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    -- HH:mm-HH:mm local-time window during which evaluation is skipped (see §I.9).
    quiet_window        VARCHAR(32),
    created_by_user_id  BIGINT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_queue_alert_rule_kind
        CHECK (kind IN ('QUEUE_P95_OVER', 'RUNNER_OFFLINE_OVER', 'STUCK_JOBS_OVER')),
    CONSTRAINT fk_queue_alert_rule_repository
        FOREIGN KEY (repository_id)
            REFERENCES repository (repository_id)
            ON DELETE CASCADE
);

CREATE INDEX idx_queue_alert_rule_enabled_kind
    ON queue_alert_rule (enabled, kind);

-- ---------------------------------------------------------------------
-- queue_alert_event: fired events. cleared_at NULL while open (dedup).
-- ---------------------------------------------------------------------
CREATE TABLE queue_alert_event (
    id                  BIGSERIAL PRIMARY KEY,
    rule_id             BIGINT NOT NULL,
    repository_id       BIGINT,
    label_set_hash      CHAR(64),
    fired_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    cleared_at          TIMESTAMPTZ,
    measured_value      INT,
    details             TEXT,
    CONSTRAINT fk_queue_alert_event_rule
        FOREIGN KEY (rule_id)
            REFERENCES queue_alert_rule (id)
            ON DELETE CASCADE
);

-- UNIQUE so concurrent evaluator threads / instances cannot create two
-- open events for the same rule (would cause duplicate emails).
CREATE UNIQUE INDEX idx_queue_alert_event_open
    ON queue_alert_event (rule_id)
    WHERE cleared_at IS NULL;
CREATE INDEX idx_queue_alert_event_fired_at
    ON queue_alert_event (fired_at DESC);
