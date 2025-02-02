-- Add creator user reference to deployment table
ALTER TABLE public.deployment ADD COLUMN user_id bigint;
ALTER TABLE public.deployment 
    ADD CONSTRAINT fk_deployment_user 
    FOREIGN KEY (user_id) REFERENCES public."user"(id);

-- Convert environment.locked_by from string to user reference
ALTER TABLE public.environment DROP COLUMN locked_by;
ALTER TABLE public.environment ADD COLUMN author_id bigint;
ALTER TABLE public.environment 
    ADD CONSTRAINT fk_environment_locked_by 
    FOREIGN KEY (author_id) REFERENCES public."user"(id);

-- Convert environment_lock_history.locked_by to user reference
ALTER TABLE public.environment_lock_history DROP COLUMN locked_by;
ALTER TABLE public.environment_lock_history ADD COLUMN author_id bigint;
ALTER TABLE public.environment_lock_history
    ADD CONSTRAINT fk_env_lock_history_user
    FOREIGN KEY (author_id) REFERENCES public."user"(id);

-- Add creator user reference to helios_deployment table 
ALTER TABLE public.helios_deployment ADD COLUMN user_id bigint;
ALTER TABLE public.helios_deployment 
    ADD CONSTRAINT fk_helios_deployment_user 
    FOREIGN KEY (user_id) REFERENCES public."user"(id);

-- Add deployment reference to helios_deployment
ALTER TABLE public.helios_deployment ADD COLUMN deployment_id bigint;
ALTER TABLE public.helios_deployment ADD COLUMN sha character varying(255);