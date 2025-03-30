alter table if exists repository add column environment_count integer not null DEFAULT 0;
alter table if exists repository add column branch_count integer not null DEFAULT 0;
alter table if exists repository add column latest_release_tag_name varchar(255);
alter table if exists repository add column pull_request_count integer not null DEFAULT 0;
create table repository_contributor (contributor_id bigint not null, user_id bigint not null, primary key (contributor_id, user_id));
alter table if exists repository_contributor add constraint FKh74u9ub2ujepmbcquonu82xb2 foreign key (user_id) references public.user on delete cascade;
alter table if exists repository_contributor add constraint FKlrcu5n700fkh90dj34d4rueb9 foreign key (contributor_id) references repository on delete cascade;
