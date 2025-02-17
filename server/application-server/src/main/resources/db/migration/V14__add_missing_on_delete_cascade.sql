-------------------------------------------------------------------------------
-- workflow_group_membership -> workflow
-------------------------------------------------------------------------------
-- Removing a workflow_group_membership row automatically removes any workflow rows that reference that release_candidate.
ALTER TABLE public.workflow_group_membership DROP CONSTRAINT fkixdlgaqu4hykyehs17gbvyvfj;
ALTER TABLE public.workflow_group_membership
    ADD CONSTRAINT fkixdlgaqu4hykyehs17gbvyvfj
        FOREIGN KEY (workflow_id)
            REFERENCES public.workflow(id)
            ON DELETE CASCADE;