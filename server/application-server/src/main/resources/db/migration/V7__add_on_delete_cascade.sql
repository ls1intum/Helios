-- This Flyway migration script updates all relevant foreign-key constraints to use ON DELETE CASCADE.
-- When a row in the parent table (e.g. repository) is deleted, all dependent rows in child tables
-- will also be automatically removed. This ensures a single DELETE statement on 'repository'
-- will cascade and remove all referencing data (branches, commits, issues, etc.).

BEGIN;

-------------------------------------------------------------------------------
-- branch -> repository
-------------------------------------------------------------------------------
-- Drops the existing FK from branch(repository_id) to repository(repository_id)
-- and re-adds it with ON DELETE CASCADE so deleting a repository row
-- also deletes associated branch rows.
ALTER TABLE public.branch DROP CONSTRAINT fkeebb6nxm58bea44qso7p3ehu4;
ALTER TABLE public.branch
    ADD CONSTRAINT fkeebb6nxm58bea44qso7p3ehu4
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- commit -> repository
-------------------------------------------------------------------------------
-- Ensures removing a repository row cascades to commits tied to that repository.
ALTER TABLE public.commit DROP CONSTRAINT fkgqmkfk1wovdbkmbanfrrxc9pp;
ALTER TABLE public.commit
    ADD CONSTRAINT fkgqmkfk1wovdbkmbanfrrxc9pp
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- deployment -> repository
-------------------------------------------------------------------------------
-- Ensures a deployment row will be removed if its repository is deleted.
ALTER TABLE public.deployment DROP CONSTRAINT fksjgd2dgfjwp7moqk366hg8c04;
ALTER TABLE public.deployment
    ADD CONSTRAINT fksjgd2dgfjwp7moqk366hg8c04
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- environment -> repository
-------------------------------------------------------------------------------
-- Cascades removal from repository to environment rows referencing it.
ALTER TABLE public.environment DROP CONSTRAINT fklifflq2nvs3cfi5gh23u5kg45;
ALTER TABLE public.environment
    ADD CONSTRAINT fklifflq2nvs3cfi5gh23u5kg45
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- issue -> repository
-------------------------------------------------------------------------------
-- Deleting a repository automatically removes all issues linked to it.
ALTER TABLE public.issue DROP CONSTRAINT fk76s4b6ncspm9bk35y49xh4s9t;
ALTER TABLE public.issue
    ADD CONSTRAINT fk76s4b6ncspm9bk35y49xh4s9t
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- label -> repository
-------------------------------------------------------------------------------
-- Ensures labels tied to a specific repository are deleted when the repository is removed.
ALTER TABLE public.label DROP CONSTRAINT fk2951edbl9g9y8ee1q97e2ff75;
ALTER TABLE public.label
    ADD CONSTRAINT fk2951edbl9g9y8ee1q97e2ff75
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- workflow -> repository
-------------------------------------------------------------------------------
-- Removes workflow rows when their parent repository is deleted.
ALTER TABLE public.workflow DROP CONSTRAINT fk279rn80j1a5ddkj04bemt907w;
ALTER TABLE public.workflow
    ADD CONSTRAINT fk279rn80j1a5ddkj04bemt907w
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- workflow_run -> repository
-------------------------------------------------------------------------------
-- Ensures workflow_run rows tied to a repository are removed if that repository goes away.
ALTER TABLE public.workflow_run DROP CONSTRAINT fk3rcdfx1e2c3yrvadr98pxn2m2;
ALTER TABLE public.workflow_run
    ADD CONSTRAINT fk3rcdfx1e2c3yrvadr98pxn2m2
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;


-------------------------------------------------------------------------------
-- repository_settings -> repository
-------------------------------------------------------------------------------
-- Removes repository_settings entries whenever their parent repository is deleted.
ALTER TABLE public.repository_settings DROP CONSTRAINT fkg5gupw73hms8gs1yblxw6x6mv;
ALTER TABLE public.repository_settings
    ADD CONSTRAINT fkg5gupw73hms8gs1yblxw6x6mv
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- environment_lock_history -> environment
-------------------------------------------------------------------------------
-- When an environment is removed, environment_lock_history rows referencing it also go away.
ALTER TABLE public.environment_lock_history DROP CONSTRAINT fkb7t7o88en911u1wjg59ldt75b;
ALTER TABLE public.environment_lock_history
    ADD CONSTRAINT fkb7t7o88en911u1wjg59ldt75b
        FOREIGN KEY (environment_id)
            REFERENCES public.environment(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- helios_deployment -> environment
-------------------------------------------------------------------------------
-- Cascades environment deletion to helios_deployment rows referencing that environment.
ALTER TABLE public.helios_deployment DROP CONSTRAINT fkt970s49c8fv0vdnw2ssovbpia;
ALTER TABLE public.helios_deployment
    ADD CONSTRAINT fkt970s49c8fv0vdnw2ssovbpia
        FOREIGN KEY (environment_id)
            REFERENCES public.environment(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- installed_apps -> environment
-------------------------------------------------------------------------------
-- Ensures removing an environment also removes any installed_apps rows referencing it.
ALTER TABLE public.installed_apps DROP CONSTRAINT fkpfj5pd1sbcahyiitsl8ce8vvb;
ALTER TABLE public.installed_apps
    ADD CONSTRAINT fkpfj5pd1sbcahyiitsl8ce8vvb
        FOREIGN KEY (environment_id)
            REFERENCES public.environment(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- deployment -> environment
-------------------------------------------------------------------------------
-- If an environment row is deleted, related deployment rows will also be removed.
ALTER TABLE public.deployment DROP CONSTRAINT fk2wdyfioym1epj5p2f1lgsl7dx;
ALTER TABLE public.deployment
    ADD CONSTRAINT fk2wdyfioym1epj5p2f1lgsl7dx
        FOREIGN KEY (environment_id)
            REFERENCES public.environment(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- deployment -> issue (pull_request_id)
-------------------------------------------------------------------------------
-- Deleting an issue automatically removes deployment rows referencing that issue as a pull request.
ALTER TABLE public.deployment DROP CONSTRAINT fkbjy892k9b0c92wvqc407cda3b;
ALTER TABLE public.deployment
    ADD CONSTRAINT fkbjy892k9b0c92wvqc407cda3b
        FOREIGN KEY (pull_request_id)
            REFERENCES public.issue(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- issue_assignee -> issue
-------------------------------------------------------------------------------
-- Deleting an issue row removes assignee references for that issue.
ALTER TABLE public.issue_assignee DROP CONSTRAINT fk2cfu8w8wjb9vosy4hbrme0rqe;
ALTER TABLE public.issue_assignee
    ADD CONSTRAINT fk2cfu8w8wjb9vosy4hbrme0rqe
        FOREIGN KEY (issue_id)
            REFERENCES public.issue(id)
            ON DELETE CASCADE;


-- Ensure issue_assignee rows are deleted when their associated issue is deleted
ALTER TABLE public.issue_assignee DROP CONSTRAINT fkocgmsva4p5e8ic9k5dbjqa15u;
ALTER TABLE public.issue_assignee
    ADD CONSTRAINT fkocgmsva4p5e8ic9k5dbjqa15u
        FOREIGN KEY (issue_id)
            REFERENCES public.issue(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- issue_label -> issue
-------------------------------------------------------------------------------
-- Ensures removing an issue also deletes all related labels on that issue.
ALTER TABLE public.issue_label DROP CONSTRAINT fkit5n9c0frugu5m8xqsxtps63r;
ALTER TABLE public.issue_label
    ADD CONSTRAINT fkit5n9c0frugu5m8xqsxtps63r
        FOREIGN KEY (issue_id)
            REFERENCES public.issue(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- pull_request_requested_reviewers -> issue
-------------------------------------------------------------------------------
-- If the pull request (issue) is removed, any requested reviewers entries disappear as well.
ALTER TABLE public.pull_request_requested_reviewers DROP CONSTRAINT fk6dld06xx8rh9xhqfnca070a0i;
ALTER TABLE public.pull_request_requested_reviewers
    ADD CONSTRAINT fk6dld06xx8rh9xhqfnca070a0i
        FOREIGN KEY (pull_request_id)
            REFERENCES public.issue(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- issue_workflow_runs -> issue
-------------------------------------------------------------------------------
-- The issue_workflow_runs table has multiple FKs. One references issue.id for pull_request_id.
-- We add ON DELETE CASCADE so removing an issue also removes linked entries in issue_workflow_runs.
ALTER TABLE public.issue_workflow_runs DROP CONSTRAINT fkk489xsqux2u0oh20ax532hu40;
ALTER TABLE public.issue_workflow_runs
    ADD CONSTRAINT fkk489xsqux2u0oh20ax532hu40
        FOREIGN KEY (pull_request_id)
            REFERENCES public.issue(id)
            ON DELETE CASCADE;

-- When a workflow_run is removed, any associated issue_workflow_runs rows are also removed.
ALTER TABLE public.issue_workflow_runs DROP CONSTRAINT fkbcqvyqbndm5422rxf58vd134g;
ALTER TABLE public.issue_workflow_runs
  ADD CONSTRAINT fkbcqvyqbndm5422rxf58vd134g
  FOREIGN KEY (workflow_runs_id)
  REFERENCES public.workflow_run(id)
  ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- workflow_run_pull_requests -> issue
-------------------------------------------------------------------------------
-- Removing an issue also removes any workflow_run_pull_requests referencing it.
ALTER TABLE public.workflow_run_pull_requests DROP CONSTRAINT fkb4tp79ihvjiuqi4lbx8gqvqih;
ALTER TABLE public.workflow_run_pull_requests
    ADD CONSTRAINT fkb4tp79ihvjiuqi4lbx8gqvqih
        FOREIGN KEY (pull_requests_id)
            REFERENCES public.issue(id)
            ON DELETE CASCADE;


-- When a workflow_run is removed, any associated workflow_run_pull_requests rows are also removed.
ALTER TABLE public.workflow_run_pull_requests DROP CONSTRAINT fkbpd29gs51yccp7xydinuexlw;
ALTER TABLE public.workflow_run_pull_requests
  ADD CONSTRAINT fkbpd29gs51yccp7xydinuexlw
  FOREIGN KEY (workflow_run_id)
  REFERENCES public.workflow_run(id)
  ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- environment_status -> environment
-------------------------------------------------------------------------------
ALTER TABLE public.environment_status DROP CONSTRAINT IF EXISTS environment_status_environment_id_fkey;
ALTER TABLE public.environment_status
    ADD CONSTRAINT environment_status_environment_id_fkey
        FOREIGN KEY (environment_id)
            REFERENCES public.environment(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- workflow_group -> repository_settings
-------------------------------------------------------------------------------
ALTER TABLE public.workflow_group DROP CONSTRAINT fklu121d5bqgup9xi7va5jyqqq7;
ALTER TABLE public.workflow_group
    ADD CONSTRAINT fklu121d5bqgup9xi7va5jyqqq7
        FOREIGN KEY (repository_settings_id)
            REFERENCES public.repository_settings(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- release_candidate -> repository
-------------------------------------------------------------------------------
ALTER TABLE public.release_candidate DROP CONSTRAINT FKdj9bgiudh8pae58vb7k1hy4pq;
ALTER TABLE public.release_candidate
    ADD CONSTRAINT FKdj9bgiudh8pae58vb7k1hy4pq
        FOREIGN KEY (repository_id)
            REFERENCES public.repository(repository_id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- release_candidate -> commit
-------------------------------------------------------------------------------
ALTER TABLE public.release_candidate DROP CONSTRAINT FK3o93c1y4ayqlobwid7ipkc2l4;
ALTER TABLE public.release_candidate
    ADD CONSTRAINT FK3o93c1y4ayqlobwid7ipkc2l4
        FOREIGN KEY (commit_repository_id, commit_sha)
            REFERENCES public.commit(repository_id, sha)
            ON DELETE CASCADE;


-------------------------------------------------------------------------------
-- release_candidate_evaluation -> user
-------------------------------------------------------------------------------
-- Removing a user also removes release_candidate_evaluation rows associated with them
ALTER TABLE public.release_candidate_evaluation DROP CONSTRAINT FKerigvj8hd9wn6jg8ahgjhxnbf;
ALTER TABLE public.release_candidate_evaluation
    ADD CONSTRAINT FKerigvj8hd9wn6jg8ahgjhxnbf
        FOREIGN KEY (evaluated_by_id)
            REFERENCES public."user"(id)
            ON DELETE CASCADE;

-------------------------------------------------------------------------------
-- release_candidate_evaluation -> release_candidate
-------------------------------------------------------------------------------
-- Removing a release_candidate row automatically removes any release_candidate_evaluation rows that reference that release_candidate.
ALTER TABLE public.release_candidate_evaluation DROP CONSTRAINT FKrwgk63y2dr7how94g6a1q3sq2;
ALTER TABLE public.release_candidate_evaluation
    ADD CONSTRAINT FKrwgk63y2dr7how94g6a1q3sq2
        FOREIGN KEY (release_candidate_name, release_candidate_repository_id)
            REFERENCES public.release_candidate(name, repository_id)
            ON DELETE CASCADE;

COMMIT;
