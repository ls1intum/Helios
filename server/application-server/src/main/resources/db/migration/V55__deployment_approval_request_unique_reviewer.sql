-- The deployment-approval find-or-create logic (ApprovalService.notifyReviewers and
-- DeploymentReviewActionService.findOrCreateForReviewer) assumes at most one row per
-- (deployment, reviewer_login). Nothing enforced that, so a concurrent/redelivered
-- `deployment_status: waiting` event racing an in-app click could insert two rows for the
-- same pair, after which the find-or-create lock query (Optional result) throws
-- NonUniqueResultException on the reviewer's next click. Enforce the invariant in the schema:
-- the unique index makes duplicates impossible and turns the rare insert race into a
-- constraint violation that the webhook simply reprocesses on redelivery.

-- Defensive de-duplication for any rows that already raced in before this constraint exists.
-- Keep the most-progressed row per (deployment, reviewer_login): a responded row over an
-- unresponded one, then the highest id.
DELETE FROM public.deployment_approval_request a
    USING public.deployment_approval_request b
WHERE a.helios_deployment_id = b.helios_deployment_id
  AND a.reviewer_login = b.reviewer_login
  AND a.id <> b.id
  AND (
        (a.responded_at IS NULL AND b.responded_at IS NOT NULL)
        OR (a.responded_at IS NULL AND b.responded_at IS NULL AND a.id < b.id)
        OR (a.responded_at IS NOT NULL AND b.responded_at IS NOT NULL AND a.id < b.id)
    );

CREATE UNIQUE INDEX uniq_dar_deployment_reviewer_login
    ON public.deployment_approval_request (helios_deployment_id, reviewer_login);
