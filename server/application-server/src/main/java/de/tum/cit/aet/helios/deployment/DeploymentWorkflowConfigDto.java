package de.tum.cit.aet.helios.deployment;

public record DeploymentWorkflowConfigDto(
    Long workflowId,
    String deployJobName) {

  public static DeploymentWorkflowConfigDto fromConfig(DeploymentWorkflowConfig config) {
    return new DeploymentWorkflowConfigDto(config.getWorkflow().getId(), config.getDeployJobName());
  }
}
