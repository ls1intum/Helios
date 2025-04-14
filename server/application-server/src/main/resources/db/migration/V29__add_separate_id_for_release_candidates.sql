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
    release_id BIGINT,
    body TEXT,
    CONSTRAINT fk_rc_new_repository FOREIGN KEY (repository_id) REFERENCES repository(repository_id) ON DELETE CASCADE,
    CONSTRAINT fk_rc_new_commit FOREIGN KEY (commit_repository_id, commit_sha) REFERENCES commit(repository_id, sha) ON DELETE CASCADE,
    CONSTRAINT uk_rc_new_repository_name UNIQUE (repository_id, name)
);

-- Add constraint for branch
ALTER TABLE release_candidate_new
ADD CONSTRAINT fk_rc_new_branch FOREIGN KEY (branch_repository_id, branch_name) 
REFERENCES branch(repository_id, name);

-- Add constraint for created_by
ALTER TABLE release_candidate_new
ADD CONSTRAINT fk_rc_new_created_by FOREIGN KEY (created_by_id)
REFERENCES "user"(id);

-- Add constraint for release
ALTER TABLE release_candidate_new
ADD CONSTRAINT fk_rc_new_release FOREIGN KEY (release_id)
REFERENCES release(id);

-- Add unique constraint for release_id
ALTER TABLE release_candidate_new
ADD CONSTRAINT uk_rc_new_release_id UNIQUE (release_id);

-- Step 2: Copy data from old table to new table
INSERT INTO release_candidate_new (
    repository_id, name, created_at, branch_name, branch_repository_id, 
    commit_repository_id, commit_sha, created_by_id, release_id, body
)
SELECT 
    repository_id, name, created_at, branch_name, branch_repository_id, 
    commit_repository_id, commit_sha, created_by_id, release_id, body
FROM release_candidate;

-- Step 3: Create a temporary mapping table to store the relation between old composite keys and new IDs
CREATE TABLE release_candidate_id_mapping (
    repository_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    new_id BIGINT NOT NULL,
    PRIMARY KEY (repository_id, name),
    FOREIGN KEY (new_id) REFERENCES release_candidate_new(id)
);

-- Step 4: Populate the mapping table
INSERT INTO release_candidate_id_mapping (repository_id, name, new_id)
SELECT repository_id, name, id
FROM release_candidate_new;

-- Step 5: Create a new evaluations table with ID
CREATE TABLE release_candidate_evaluation_new (
    id BIGSERIAL PRIMARY KEY,
    release_candidate_id BIGINT NOT NULL,
    evaluated_by_id BIGINT NOT NULL,
    is_working BOOLEAN NOT NULL,
    CONSTRAINT fk_rce_new_rc FOREIGN KEY (release_candidate_id) REFERENCES release_candidate_new(id) ON DELETE CASCADE,
    CONSTRAINT fk_rce_new_user FOREIGN KEY (evaluated_by_id) REFERENCES "user"(id) ON DELETE CASCADE,
    CONSTRAINT uk_rce_new_rc_user UNIQUE (release_candidate_id, evaluated_by_id)
);

-- Step 6: Migrate evaluations to new table using the mapping
INSERT INTO release_candidate_evaluation_new (release_candidate_id, evaluated_by_id, is_working)
SELECT 
    rcm.new_id, rce.evaluated_by_id, rce.is_working
FROM release_candidate_evaluation rce
JOIN release_candidate_id_mapping rcm 
    ON rce.release_candidate_repository_id = rcm.repository_id 
    AND rce.release_candidate_name = rcm.name;

-- Step 7: Backup old tables (in case we need to rollback)
ALTER TABLE release_candidate RENAME TO release_candidate_old;
ALTER TABLE release_candidate_evaluation RENAME TO release_candidate_evaluation_old;

-- Step 8: Rename new tables to original names
ALTER TABLE release_candidate_new RENAME TO release_candidate;
ALTER TABLE release_candidate_evaluation_new RENAME TO release_candidate_evaluation;

-- Step 9: Add any necessary indexes that were on the old tables
CREATE INDEX idx_release_candidate_repository_id ON release_candidate(repository_id);
CREATE INDEX idx_release_candidate_created_by_id ON release_candidate(created_by_id);
CREATE INDEX idx_release_candidate_branch_repository_id ON release_candidate(branch_repository_id, branch_name);
CREATE INDEX idx_release_candidate_evaluation_rc_id ON release_candidate_evaluation(release_candidate_id);
CREATE INDEX idx_release_candidate_evaluation_user_id ON release_candidate_evaluation(evaluated_by_id);

-- Step 10: Drop mapping table
DROP TABLE release_candidate_id_mapping;

-- Step 11: Drop old tables
DROP TABLE release_candidate_evaluation_old;
DROP TABLE release_candidate_old;