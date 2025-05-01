
ALTER TABLE public."user"
ADD COLUMN has_logged_in BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN notifications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
ADD COLUMN notification_email VARCHAR(255);

-- Populate notification email from email
UPDATE public."user"
SET notification_email = email
WHERE email IS NOT NULL;

