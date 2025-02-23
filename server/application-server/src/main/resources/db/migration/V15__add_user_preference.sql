create table
    public.user_preference (
        id bigint not null,
        user_id bigint not null,
        primary key (id)
    );

ALTER TABLE public.user_preference ALTER COLUMN id ADD GENERATED BY DEFAULT AS IDENTITY (
    SEQUENCE NAME public.user_preference_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

alter table if exists public.user_preference
drop constraint if exists UKs5oeayykfc7bpkpdwyrffwcqx;

alter table if exists public.user_preference add constraint UKs5oeayykfc7bpkpdwyrffwcqx unique (user_id);

create table
    user_preference_favourite_branches (
        user_preference_id bigint not null,
        favourite_branches_name varchar(255) not null,
        favourite_branches_repository_id bigint not null,
        primary key (
            user_preference_id,
            favourite_branches_name,
            favourite_branches_repository_id
        )
    );

create table
    user_preference_favourite_pull_requests (
        user_preference_id bigint not null,
        favourite_pull_requests_id bigint not null,
        primary key (user_preference_id, favourite_pull_requests_id)
    );

alter table if exists public.user_preference add constraint FKq5oj1co3wu38ltb5g1xg9wel4 foreign key (user_id) references public.user;

alter table if exists user_preference_favourite_branches add constraint FKgyxf2ri6f3j4pourba7shdsma foreign key (
    favourite_branches_repository_id,    
    favourite_branches_name
) references branch;

alter table if exists user_preference_favourite_branches add constraint FKj734saube6d10apw9l8q038e3 foreign key (user_preference_id) references public.user_preference;

alter table if exists user_preference_favourite_pull_requests add constraint FK773fsk43ewsg4oy5ut1hvslec foreign key (favourite_pull_requests_id) references issue;

alter table if exists user_preference_favourite_pull_requests add constraint FKtnbluehl24n2x8nwh2gxvqr8 foreign key (user_preference_id) references public.user_preference;