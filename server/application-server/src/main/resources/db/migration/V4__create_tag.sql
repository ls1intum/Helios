create table
    tag (
        name varchar(255) not null,
        created_at timestamp(6)
        with
            time zone,
            repository_id bigint not null,
            branch_name varchar(255),
            branch_repository_id bigint,
            commit_repository_id bigint not null,
            commit_sha varchar(255) not null,
            created_by_id bigint,
            primary key (name, repository_id)
    );

create table
    tag_evaluation (
        is_working boolean not null,
        evaluated_by_id bigint not null,
        tag_name varchar(255) not null,
        tag_repository_id bigint not null,
        primary key (evaluated_by_id, tag_name, tag_repository_id)
    );


alter table only tag add constraint pkey_commit_repository_id_commit_sha unique (commit_repository_id, commit_sha);
alter table only tag add constraint FKdj9bgiudh8pae58vb7k1hy4pq foreign key (repository_id) references repository;
alter table only tag add constraint FK3o93c1y4ayqlobwid7ipkc2l4 foreign key (commit_repository_id, commit_sha) references commit;
alter table only tag add constraint FKtpgs4u8l5skgjlr95atu96yqt foreign key (branch_repository_id, branch_name) references branch;
alter table only tag add constraint FKlvm85yfqra0n9dmqrfaeqh2hj foreign key (created_by_id) references public.user;
alter table only public.user add constraint unique_user_login unique (login);
alter table if exists tag_evaluation add constraint FKerigvj8hd9wn6jg8ahgjhxnbf foreign key (evaluated_by_id) references public.user;
alter table if exists tag_evaluation add constraint FKrwgk63y2dr7how94g6a1q3sq2 foreign key (tag_name, tag_repository_id) references tag;
