-- Issue.body was accidentally a PostgreSQL Large Object: @Lob on a String maps to an `oid` column
-- on PostgreSQL (unlike release.body / release_candidate.body, which are plain `text`). Large Objects
-- cannot be read in auto-commit mode, which is what forced the connection-level auto-commit workaround
-- and produced the "Large Objects may not be used in auto-commit mode" 500s. Convert it to a plain
-- text column so the body reads like any other column, in any transaction mode.

ALTER TABLE public.issue ADD COLUMN body_text text;

-- Copy existing Large Object contents into the text column. Guard on the oid still pointing at a live
-- large object so an orphaned reference becomes NULL instead of failing the migration.
UPDATE public.issue
SET body_text = convert_from(lo_get(body), 'UTF8')
WHERE body IS NOT NULL
  AND EXISTS (SELECT 1 FROM pg_largeobject_metadata m WHERE m.oid = public.issue.body);

-- Reclaim the now-copied large objects so they don't linger in pg_largeobject.
SELECT lo_unlink(body)
FROM public.issue
WHERE body IS NOT NULL
  AND EXISTS (SELECT 1 FROM pg_largeobject_metadata m WHERE m.oid = public.issue.body);

ALTER TABLE public.issue DROP COLUMN body;
ALTER TABLE public.issue RENAME COLUMN body_text TO body;
