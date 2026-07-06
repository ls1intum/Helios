-- workflow_job: individual GitHub Actions jobs within a workflow_run, persisted so the pipeline
-- view can render per-stage nodes (e.g. "Build / Build .war artifact") rather than only whole runs.
-- Consolidated CI exposes Build/Test/Quality/E2E as jobs inside a single orchestrator run, so
-- job-level data is what makes those stages visible. The GitHub job id is the primary key; status
-- and conclusion reuse the workflow_run enum vocabulary.
CREATE TABLE public.workflow_job (
    id bigint NOT NULL,
    created_at timestamp(6) with time zone,
    updated_at timestamp(6) with time zone,
    started_at timestamp(6) with time zone,
    completed_at timestamp(6) with time zone,
    repository_id bigint,
    workflow_run_id bigint NOT NULL,
    name character varying(255),
    workflow_name character varying(255),
    status character varying(255),
    conclusion character varying(255),
    html_url character varying(255),
    head_branch character varying(255),
    head_sha character varying(255),
    CONSTRAINT workflow_job_pkey PRIMARY KEY (id),
    CONSTRAINT workflow_job_status_check CHECK (((status)::text = ANY ((ARRAY['QUEUED'::character varying, 'IN_PROGRESS'::character varying, 'COMPLETED'::character varying, 'ACTION_REQUIRED'::character varying, 'CANCELLED'::character varying, 'FAILURE'::character varying, 'NEUTRAL'::character varying, 'SKIPPED'::character varying, 'STALE'::character varying, 'SUCCESS'::character varying, 'TIMED_OUT'::character varying, 'REQUESTED'::character varying, 'WAITING'::character varying, 'PENDING'::character varying, 'UNKNOWN'::character varying])::text[]))),
    CONSTRAINT workflow_job_conclusion_check CHECK (((conclusion)::text = ANY ((ARRAY['ACTION_REQUIRED'::character varying, 'CANCELLED'::character varying, 'FAILURE'::character varying, 'NEUTRAL'::character varying, 'SUCCESS'::character varying, 'SKIPPED'::character varying, 'STALE'::character varying, 'TIMED_OUT'::character varying, 'STARTUP_FAILURE'::character varying, 'UNKNOWN'::character varying])::text[])))
);

ALTER TABLE ONLY public.workflow_job
    ADD CONSTRAINT fk_workflow_job_run FOREIGN KEY (workflow_run_id) REFERENCES public.workflow_run(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.workflow_job
    ADD CONSTRAINT fk_workflow_job_repository FOREIGN KEY (repository_id) REFERENCES public.repository(repository_id);

CREATE INDEX idx_workflow_job_run ON public.workflow_job (workflow_run_id);

CREATE INDEX idx_workflow_job_repository ON public.workflow_job (repository_id);
