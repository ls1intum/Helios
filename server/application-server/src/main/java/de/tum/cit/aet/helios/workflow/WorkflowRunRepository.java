package de.tum.cit.aet.helios.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun, Long> {
    
}
