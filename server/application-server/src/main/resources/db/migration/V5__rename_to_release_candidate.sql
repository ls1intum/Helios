ALTER TABLE tag RENAME TO release_candidate;

alter table if exists tag_evaluation drop constraint FKrwgk63y2dr7how94g6a1q3sq2;
ALTER TABLE tag_evaluation RENAME COLUMN tag_name to release_candidate_name;
ALTER TABLE tag_evaluation RENAME COLUMN tag_repository_id to release_candidate_repository_id;
ALTER TABLE tag_evaluation RENAME TO release_candidate_evaluation;

alter table if exists release_candidate_evaluation add constraint FKrwgk63y2dr7how94g6a1q3sq2 foreign key (release_candidate_name, release_candidate_repository_id) references release_candidate;
