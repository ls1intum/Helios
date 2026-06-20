-- Phase 1 of the in-Helios deployment-approval flow.
-- Tracks one row per (deployment, reviewer) for both audit and (later) email-link state.

CREATE TABLE public.deployment_approval_request (
    id                    BIGSERIAL PRIMARY KEY,
    helios_deployment_id  BIGINT      NOT NULL
        REFERENCES public.helios_deployment(id) ON DELETE CASCADE,
    reviewer_id           BIGINT
        REFERENCES public."user"(id) ON DELETE SET NULL,
    reviewer_login        VARCHAR(255) NOT NULL,
    -- SHA-256 hex of the email-link token. Plaintext is only in the URL.
    -- Null for AUTO rows where no token is ever issued.
    token_hash            VARCHAR(64),
    state                 VARCHAR(40) NOT NULL,
    via                   VARCHAR(20),
    created_at            TIMESTAMPTZ NOT NULL,
    email_sent_at         TIMESTAMPTZ,
    responded_at          TIMESTAMPTZ,
    -- Null for AUTO and IN_APP rows (no TTL); set only for email-link rows where the token
    -- expires 24h after issuance. Keeping this nullable avoids polluting future expiry sweeps
    -- with rows that have no meaningful TTL.
    expires_at            TIMESTAMPTZ,
    failure_reason        TEXT
);

CREATE INDEX idx_dar_deployment_state
    ON public.deployment_approval_request (helios_deployment_id, state);
CREATE INDEX idx_dar_reviewer_state
    ON public.deployment_approval_request (reviewer_id, state);
CREATE UNIQUE INDEX uniq_dar_token_hash
    ON public.deployment_approval_request (token_hash)
    WHERE token_hash IS NOT NULL;

-- Track the auto-approval decision on the deployment itself for fast UI lookups
-- and audit visibility without joining the approval table.
ALTER TABLE public.helios_deployment
    ADD COLUMN auto_approval_decision VARCHAR(40),
    ADD COLUMN auto_approval_at       TIMESTAMPTZ;
