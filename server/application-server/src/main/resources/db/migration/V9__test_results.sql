create table test_result (
   id              bigserial primary key,
   workflow_run_id bigint not null,
   total           int not null,
   passed          int not null,
   failures        int not null,
   errors          int not null,
   skipped         int not null,
   foreign key ( workflow_run_id )
      references workflow_run ( id )
);

alter table workflow drop constraint workflow_label_check;