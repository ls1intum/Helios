ALTER TABLE public.helios_deployment
    DROP CONSTRAINT helios_deployment_status_check;

ALTER TABLE public.helios_deployment
    ADD CONSTRAINT helios_deployment_status_check CHECK (
        (status)::text = ANY ((ARRAY [
            'WAITING'::character varying,
            'QUEUED'::character varying,
            'IN_PROGRESS'::character varying,
            'DEPLOYMENT_SUCCESS'::character varying,
            'FAILED'::character varying,
            'IO_ERROR'::character varying,
            'CANCELLED'::character varying,
            'UNKNOWN'::character varying
            ])::text[])
        );
