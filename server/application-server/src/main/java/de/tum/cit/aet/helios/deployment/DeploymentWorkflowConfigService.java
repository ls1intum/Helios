package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeploymentWorkflowConfigService {

  private final DeploymentWorkflowConfigRepository configRepository;
  private final WorkflowRepository workflowRepository;

  public Optional<DeploymentWorkflowConfigDto> findByWorkflowId(Long workflowId) {
    return configRepository.findByWorkflowId(workflowId).map(DeploymentWorkflowConfigDto::fromConfig);
  }

  public Optional<DeploymentWorkflowConfig> findConfigByWorkflow(Workflow workflow) {
    return configRepository.findByWorkflow(workflow);
  }

  @Transactional
  public DeploymentWorkflowConfigDto upsert(Long repositoryId, Long workflowId, DeploymentWorkflowConfigDto dto) 
  {
    Workflow workflow =
        workflowRepository
            .findById(workflowId)
            .orElseThrow(
                () -> new IllegalArgumentException("Workflow not found: " + workflowId));

    if (!workflow.getRepository().getRepositoryId().equals(repositoryId)) {
      throw new IllegalArgumentException(
          "Workflow " + workflowId + " does not belong to repository " + repositoryId);
    }

    DeploymentWorkflowConfig config =
        configRepository.findByWorkflow(workflow).orElseGet(DeploymentWorkflowConfig::new);

    config.setWorkflow(workflow);
    config.setDeployJobName(dto.deployJobName());

    return DeploymentWorkflowConfigDto.fromConfig(configRepository.save(config));
  }
}
