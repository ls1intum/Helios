package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.workflow.Workflow;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentWorkflowConfigRepository
    extends JpaRepository<DeploymentWorkflowConfig, Long> {

  Optional<DeploymentWorkflowConfig> findByWorkflow(Workflow workflow);

  Optional<DeploymentWorkflowConfig> findByWorkflowId(Long workflowId);
}
