-- First, truncate existing messages that are longer than 255 characters
UPDATE public.commit SET message = substring(message from 1 for 255) WHERE length(message) > 255;
-- Then alter the column to reduce its maximum length
ALTER TABLE public.commit ALTER COLUMN message TYPE character varying(255);