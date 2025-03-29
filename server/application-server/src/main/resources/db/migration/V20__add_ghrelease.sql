create table
    public.release (
        id bigint not null,
        created_at timestamp(6)
        with
            time zone,
            updated_at timestamp(6)
        with
            time zone,
            body text,
            github_url varchar(255),
            is_draft boolean,
            is_prerelease boolean,
            name varchar(255),
            published_at timestamp(6)
        with
            time zone,
            tag_name varchar(255),
            repository_id bigint,
            primary key (id)
    );

alter table if exists public.release
drop constraint if exists UKroicl6ap0hedm08ivbpvj4bji;

alter table if exists public.release add constraint UKroicl6ap0hedm08ivbpvj4bji unique (tag_name, repository_id);

alter table if exists public.release
drop constraint if exists UK5ksyl3m7jp7dmsd6bcutkv7s;

alter table if exists public.release add constraint FK3ikmsdlts7wm4wa8p1nc793f5 foreign key (repository_id) references repository;

alter table if exists public.release
drop constraint if exists UKmylm8hcj8kam5t1s8spw9uds8;

alter table if exists public.release add constraint UKmylm8hcj8kam5t1s8spw9uds8 unique (tag_name, repository_id);

alter table if exists release_candidate
add column release_id bigint;

alter table if exists release_candidate
drop constraint if exists UK4dud3l2tq5mfv86psi7awtqgw;

alter table if exists release_candidate add constraint UK4dud3l2tq5mfv86psi7awtqgw unique (release_id);

alter table if exists release_candidate add constraint FKc7u3wj6b3oh8imnwxxwgboaes foreign key (release_id) references public.release;

alter table if exists release_candidate
drop constraint if exists pkey_commit_repository_id_commit_sha;