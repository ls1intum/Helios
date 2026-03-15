ALTER TABLE public.release ADD COLUMN creator_id bigint;

ALTER TABLE public.release
    ADD CONSTRAINT fk_release_creator
    FOREIGN KEY (creator_id) REFERENCES public."user"(id);
