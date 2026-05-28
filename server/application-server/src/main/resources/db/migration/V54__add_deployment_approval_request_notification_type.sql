-- Phase 3 (in-app) of the deployment-approval flow: notify required reviewers by email
-- when a deployment lands in DEFERRED_TO_REVIEWERS. The new notification type follows the
-- existing DEPLOYMENT_FAILED / LOCK_EXPIRED / LOCK_UNLOCKED pattern.

ALTER TABLE public.notification_preference
    DROP CONSTRAINT IF EXISTS chk_notification_type;

ALTER TABLE public.notification_preference
    ADD CONSTRAINT chk_notification_type
        CHECK (type IN (
                        'DEPLOYMENT_FAILED',
                        'LOCK_EXPIRED',
                        'LOCK_UNLOCKED',
                        'DEPLOYMENT_APPROVAL_REQUEST'
            ));

-- Backfill: enable DEPLOYMENT_APPROVAL_REQUEST by default for every existing user. Without this
-- row, NotificationEligibilityService.canNotify defaults to false (no row → no notification),
-- which would mean reviewers who never visited their preferences page silently miss approval
-- emails. For an operationally critical, deployment-blocking notification we want default-on.
-- Users who don't want approval emails can disable it in their preferences page.
INSERT INTO public.notification_preference (user_id, type, enabled)
SELECT u.id, 'DEPLOYMENT_APPROVAL_REQUEST', TRUE
FROM public."user" u
ON CONFLICT (user_id, type) DO NOTHING;
