-- Migration: Convert ReleaseCandidate from composite key to generated ID

-- Step 1: Create new tables with generated IDs
CREATE TABLE release_candidate_new (
    id BIGSERIAL PRIMARY KEY,
    repository_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE,
    branch_name VARCHAR(255),
    branch_repository_id BIGINT,
    commit_repository_id BIGINT NOT NULL,
    commit_sha VARCHAR(255) NOT NULL,
    created_by_id BIGINT,
    release_id BIGINT UNIQUE,
    body TEXT,
    CONSTRAINT fk_rc_new_repository FOREIGN KEY (repository_id) REFERENCES repository(repository_id) ON DELETE CASCADE,
    CONSTRAINT fk_rc_new_commit FOREIGN KEY (commit_repository_id, commit_sha) REFERENCES commit(repository_id, sha) ON DELETE SET NULL,
    CONSTRAINT fk_rc_new_branch FOREIGN KEY (branch_repository_id, branch_name) REFERENCES branch(repository_id, name) ON DELETE SET NULL,
    CONSTRAINT fk_rc_new_created_by FOREIGN KEY (created_by_id) REFERENCES "user"(id) ON DELETE SET NULL,
    CONSTRAINT fk_rc_new_release FOREIGN KEY (release_id) REFERENCES release(id) ON DELETE CASCADE,
    CONSTRAINT uk_rc_new_repository_name UNIQUE (repository_id, name)
);

-- Step 2: Copy data from old table to new table
INSERT INTO release_candidate_new (
    repository_id, name, created_at, branch_name, branch_repository_id, 
    commit_repository_id, commit_sha, created_by_id, release_id, body
)
SELECT 
    repository_id, name, created_at, branch_name, branch_repository_id, 
    commit_repository_id, commit_sha, created_by_id, release_id, body
FROM release_candidate;

-- Step 3: Create new evaluations table with a separate id column
CREATE TABLE release_candidate_evaluation_new (
    id BIGSERIAL PRIMARY KEY,
    release_candidate_id BIGINT NOT NULL,
    evaluated_by_id BIGINT NOT NULL,
    is_working BOOLEAN NOT NULL,
    CONSTRAINT fk_rce_new_rc FOREIGN KEY (release_candidate_id) REFERENCES release_candidate_new(id) ON DELETE CASCADE,
    CONSTRAINT fk_rce_new_user FOREIGN KEY (evaluated_by_id) REFERENCES "user"(id) ON DELETE CASCADE,
    CONSTRAINT uk_rce_new_rc_user UNIQUE (release_candidate_id, evaluated_by_id)
);

-- Step 4: Populate new evaluations table
INSERT INTO release_candidate_evaluation_new (release_candidate_id, evaluated_by_id, is_working)
SELECT 
    rcn.id, rce.evaluated_by_id, rce.is_working
FROM release_candidate_evaluation rce
JOIN release_candidate_new rcn 
    ON rce.release_candidate_repository_id = rcn.repository_id 
    AND rce.release_candidate_name = rcn.name;


-- Step 5: Backup old tables (in case we need to rollback)
ALTER TABLE release_candidate RENAME TO release_candidate_old;
ALTER TABLE release_candidate_evaluation RENAME TO release_candidate_evaluation_old;

-- Step 6: Rename new tables to original names
ALTER TABLE release_candidate_new RENAME TO release_candidate;
ALTER TABLE release_candidate_evaluation_new RENAME TO release_candidate_evaluation;

-- Step 7: Create indexes more efficiently grouped by table
CREATE INDEX idx_release_candidate_repository_id ON release_candidate(repository_id);

-- Steps 8: Clean up temporary tables in one transaction for atomicity
BEGIN;
DROP TABLE release_candidate_evaluation_old;
DROP TABLE release_candidate_old;
COMMIT;

-- Add ON DELETE CASCADE for release table
ALTER TABLE public.release DROP CONSTRAINT FK3ikmsdlts7wm4wa8p1nc793f5;
ALTER TABLE public.release
    ADD CONSTRAINT FK3ikmsdlts7wm4wa8p1nc793f5
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;