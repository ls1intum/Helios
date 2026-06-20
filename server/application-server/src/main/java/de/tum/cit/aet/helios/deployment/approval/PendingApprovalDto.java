package de.tum.cit.aet.helios.deployment.approval;

import de.tum.cit.aet.helios.heliosdeployment.HeliosDeployment;
import java.time.OffsetDateTime;

/**
 * Summary row used by the in-app pending-approvals list. Captures just enough about the deployment
 * for the UI to render a row + a context tooltip without further round trips.
 */
public record PendingApprovalDto(
    Long approvalRequestId,
    Long deploymentId,
    Long workflowRunId,
    String workflowRunHtmlUrl,
    Long environmentId,
    String environmentName,
    Long repositoryId,
    String repositoryNameWithOwner,
    String creatorLogin,
    String sourceBranchName,
    String sha,
    OffsetDateTime requestedAt) {

  public static PendingApprovalDto from(DeploymentApprovalRequest request) {
    HeliosDeployment deployment = request.getHeliosDeployment();
    return new PendingApprovalDto(
        request.getId(),
        deployment.getId(),
        deployment.getWorkflowRunId(),
        deployment.getWorkflowRunHtmlUrl(),
        deployment.getEnvironment().getId(),
        deployment.getEnvironment().getName(),
        deployment.getEnvironment().getRepository() != null
            ? deployment.getEnvironment().getRepository().getRepositoryId()
            : null,
        deployment.getEnvironment().getRepository() != null
            ? deployment.getEnvironment().getRepository().getNameWithOwner()
            : null,
        deployment.getCreator() != null ? deployment.getCreator().getLogin() : null,
        deployment.getSourceBranchName(),
        deployment.getSha(),
        request.getCreatedAt());
  }
}
