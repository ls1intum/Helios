create table test_suite (
   id              bigserial primary key,
   workflow_run_id bigint not null
      references workflow_run ( id )
         on delete cascade,
   name            varchar(500) not null,
   timestamp       timestamp(6) not null,
   tests           integer not null check ( tests >= 0 ),
   failures        integer not null check ( failures >= 0 ),
   errors          integer not null check ( errors >= 0 ),
   skipped         integer not null check ( skipped >= 0 ),
   time            double precision not null
);

create table test_case (
   id            bigserial primary key,
   test_suite_id bigint not null
      references test_suite ( id )
         on delete cascade,
   name          varchar(255) not null,
   class_name    varchar(255),
   time          double precision not null,
   status        varchar(20) not null,
   error_type    varchar(255),
   message       text,
   stack_trace   text
);

create index idx_test_suite_run on
   test_suite (
      workflow_run_id
   );
create index idx_test_case_suite on
   test_case (
      test_suite_id
   );

create unique index idx_test_suite_run_name on
   test_suite (
      workflow_run_id,
      name
   );

alter table workflow drop constraint workflow_label_check;