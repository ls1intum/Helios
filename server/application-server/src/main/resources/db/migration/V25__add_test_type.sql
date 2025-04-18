create table public.test_type (
   id            bigint primary key
      generated by default as identity,
   name          varchar(255) not null,
   artifact_name varchar(255) not null,
   workflow_id   bigint not null,
   repository_id bigint not null,
   created_at    timestamp default current_timestamp,
   updated_at    timestamp default current_timestamp,
   constraint fk_test_type_workflow foreign key ( workflow_id )
      references public.workflow ( id )
         on delete cascade,
   constraint fk_test_type_repository foreign key ( repository_id )
      references public.repository ( repository_id )
         on delete cascade,
   constraint uk_test_type_name_repository unique ( name,
                                                    repository_id )
);

-- Index for queries by repository_id
create index idx_test_type_repository_id on
   public.test_type (
      repository_id
   );

-- Composite index for queries that filter by both id and repository_id
create index idx_test_type_id_repository_id on
   public.test_type (
      id,
      repository_id
   );

-- Index for foreign key to workflow
create index idx_test_type_workflow_id on
   public.test_type (
      workflow_id
   );

-- Index for name lookups within a repository (if you plan to search by name)
create index idx_test_type_name_repository_id on
   public.test_type (
      repository_id,
      name
   );

-- Modify test_suite to have a reference to test_type
alter table public.test_suite add column test_type_id bigint;

alter table public.test_suite
   add constraint fk_test_suite_test_type
      foreign key ( test_type_id )
         references public.test_type ( id )
            on delete set null;

create index idx_test_suite_test_type_id on
   public.test_suite (
      test_type_id
   );

-- We store our system out logs in the database
ALTER TABLE test_suite ADD COLUMN system_out TEXT;
ALTER TABLE test_case ADD COLUMN system_out TEXT;