ALTER TABLE public."user"
    ADD COLUMN has_logged_in         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN notification_email    VARCHAR(255);

-- Populate notification email from email
UPDATE public."user"
SET notification_email = email
WHERE email IS NOT NULL;

-- Create notification preference table
CREATE TABLE public.notification_preference
(
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT       NOT NULL,
    type    VARCHAR(255) NOT NULL,
    enabled BOOLEAN      NOT NULL DEFAULT TRUE,

    CONSTRAINT fk_user_notification FOREIGN KEY (user_id)
        REFERENCES public."user" (id) ON DELETE CASCADE,

    CONSTRAINT uc_user_notification_type UNIQUE (user_id, type)
);

-- Add check constraint for notification types (ENUM defined in NotificationPreference.Type)
ALTER TABLE public.notification_preference
    ADD CONSTRAINT chk_notification_type
        CHECK (type IN (
                'DEPLOYMENT_FAILED',
                'LOCK_EXPIRED',
                'LOCK_UNLOCKED'
            ));


-- DEPLOYMENT_FAILED
INSERT INTO public.notification_preference (user_id, type, enabled)
SELECT id, 'DEPLOYMENT_FAILED', TRUE
FROM public."user"
WHERE has_logged_in = TRUE
  AND NOT EXISTS (
    SELECT 1
    FROM public.notification_preference np
    WHERE np.user_id = "user".id
      AND np.type = 'DEPLOYMENT_FAILED'
);


-- LOCK_EXPIRED
INSERT INTO public.notification_preference (user_id, type, enabled)
SELECT id, 'LOCK_EXPIRED', TRUE
FROM public."user"
WHERE has_logged_in = TRUE
  AND NOT EXISTS (
    SELECT 1
    FROM public.notification_preference np
    WHERE np.user_id = "user".id
      AND np.type = 'LOCK_EXPIRED'
);

-- LOCK_UNLOCKED
INSERT INTO public.notification_preference (user_id, type, enabled)
SELECT id, 'LOCK_UNLOCKED', TRUE
FROM public."user"
WHERE has_logged_in = TRUE
  AND NOT EXISTS (
    SELECT 1
    FROM public.notification_preference np
    WHERE np.user_id = "user".id
      AND np.type = 'LOCK_UNLOCKED'
);
